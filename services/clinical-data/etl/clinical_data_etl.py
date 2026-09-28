#!/usr/bin/env python3
"""Clinical data ETL.

Every ETL_INTERVAL_SECONDS, looks for patients.csv, diagnoses.csv, labs.csv and encounters.csv in
DATA_DIR. When all four are present and their SHA-256 checksums differ from the last successful run,
it truncates and reloads the raw staging tables and calls etl.process_run, which upserts into dbo.
After every check it overwrites the single etl.heartbeat row with the time, outcome and interval.

Environment:
    POSTGRES_USER, POSTGRES_PASSWORD, DB_HOST, DB_NAME   required
    DB_HOST_PORT              default 5432
    DATA_DIR                  default /data
    ETL_INTERVAL_SECONDS      default 300
    ETL_FILE_SETTLE_SECONDS   default 30; files modified more recently are assumed still being written
    LOG_LEVEL                 default INFO

Usage:
    python clinical_data_etl.py          run forever
    python clinical_data_etl.py --once   run one cycle; exit code 1 if it failed
"""

import hashlib
import logging
import os
import signal
import sys
import threading
import time
from dataclasses import dataclass
from pathlib import Path

import psycopg
from psycopg import sql
from psycopg.conninfo import make_conninfo

# CSV file -> (raw table, columns in the order the CSV header must list them)
FILES = {
    "patients.csv": ("patients", ["patient_id", "first_name", "last_name", "date_of_birth", "gender", "phone",
                                  "language", "pcp_provider_name"]),
    "diagnoses.csv": ("diagnoses", ["patient_id", "icd_code", "description", "diagnosed_date"]),
    "labs.csv": ("labs", ["patient_id", "test_name", "result_value", "result_date"]),
    "encounters.csv": ("encounters", ["patient_id", "specialty", "encounter_date", "provider_name"]),
}

# PostgreSQL advisory lock that keeps a second ETL instance from loading at the same time
LOCK_KEY = 7_340_001

# How soon to retry when the database is down or Flyway has not created the schema yet
RETRY_SECONDS = 10

UTF8_BOM = b"\xef\xbb\xbf"

log = logging.getLogger("clinical-data-etl")


@dataclass(frozen=True)
class Config:
    data_dir: Path
    interval_seconds: int
    settle_seconds: int
    conninfo: str

    @staticmethod
    def from_env() -> "Config":
        def required(name: str) -> str:
            value = os.environ.get(name)
            if not value:
                raise SystemExit(f"Missing required environment variable {name}")
            return value

        return Config(
            data_dir=Path(os.environ.get("DATA_DIR", "/data")),
            interval_seconds=int(os.environ.get("ETL_INTERVAL_SECONDS", "300")),
            settle_seconds=int(os.environ.get("ETL_FILE_SETTLE_SECONDS", "30")),
            conninfo=make_conninfo(
                host=required("DB_HOST"),
                port=os.environ.get("DB_HOST_PORT", "5432"),
                dbname=required("DB_NAME"),
                user=required("POSTGRES_USER"),
                password=required("POSTGRES_PASSWORD"),
                application_name="clinical-data-etl",
            ),
        )


@dataclass(frozen=True)
class CsvFile:
    name: str
    path: Path
    checksum: str
    size: int


def sha256(path: Path) -> str:
    with path.open("rb") as f:
        return hashlib.file_digest(f, "sha256").hexdigest()


def scan(config: Config) -> tuple[dict[str, CsvFile] | None, str]:
    """The four files with their checksums, or None and why when they are not all present and settled."""
    missing = [name for name in FILES if not (config.data_dir / name).is_file()]
    if missing:
        return None, f"Waiting for {', '.join(missing)} in {config.data_dir}"

    now = time.time()
    unsettled = [name for name in FILES
                 if now - (config.data_dir / name).stat().st_mtime < config.settle_seconds]
    if unsettled:
        return None, f"Waiting for {', '.join(unsettled)} to finish being written"

    files = {name: CsvFile(name, config.data_dir / name, sha256(config.data_dir / name),
                           (config.data_dir / name).stat().st_size)
             for name in FILES}
    return files, ""


def schema_ready(conn: psycopg.Connection) -> bool:
    row = conn.execute("""
        SELECT to_regclass('etl.load_file') IS NOT NULL
           AND to_regclass('raw.patients') IS NOT NULL
           AND to_regclass('etl.heartbeat') IS NOT NULL
           AND to_regprocedure('etl.process_run(bigint)') IS NOT NULL""").fetchone()
    return bool(row[0])


def last_loaded_checksums(conn: psycopg.Connection) -> tuple[int | None, dict[str, str]]:
    """The last SUCCEEDED run and its file checksums."""
    run_id = conn.execute("SELECT max(run_id) FROM etl.load_run WHERE status = 'SUCCEEDED'").fetchone()[0]
    rows = conn.execute("SELECT file_name, checksum FROM etl.load_file WHERE run_id = %s", (run_id,)).fetchall()
    return run_id, dict(rows)


def close_abandoned_runs(conn: psycopg.Connection) -> None:
    """Runs left STARTED or LOADED by an ETL that stopped mid-run would block new runs forever."""
    cur = conn.execute("""
        UPDATE etl.load_run
        SET status = 'FAILED', finished_at = now(),
            error_message = 'Abandoned: the ETL stopped before the run finished'
        WHERE status IN ('STARTED', 'LOADED')""")
    if cur.rowcount:
        log.warning("Marked %d abandoned run(s) as FAILED", cur.rowcount)


def copy_into_raw(conn: psycopg.Connection, csv_file: CsvFile, table: str, columns: list[str]) -> int:
    """COPYs the CSV into its raw table and returns the row count.

    HEADER MATCH makes PostgreSQL reject a file whose header does not list exactly these columns in
    this order. The bytes are hashed while streaming, so a file that changed since it was scanned
    fails the load instead of being recorded under the wrong checksum.
    """
    statement = sql.SQL("COPY {} ({}) FROM STDIN WITH (FORMAT csv, HEADER MATCH, ENCODING 'UTF8')").format(
        sql.Identifier("raw", table), sql.SQL(", ").join(map(sql.Identifier, columns)))
    digest = hashlib.sha256()
    with conn.cursor() as cur:
        with csv_file.path.open("rb") as f, cur.copy(statement) as copy:
            first = True
            while chunk := f.read(1 << 20):
                digest.update(chunk)
                if first:
                    first = False
                    if chunk.startswith(UTF8_BOM):  # Excel-style BOM would break HEADER MATCH
                        chunk = chunk[len(UTF8_BOM):]
                copy.write(chunk)
        rows = cur.rowcount
    if digest.hexdigest() != csv_file.checksum:
        raise RuntimeError(f"{csv_file.name} changed while it was being loaded")
    return rows


def load(conn: psycopg.Connection, files: dict[str, CsvFile]) -> tuple[int, int]:
    """Loads the files as a new run; returns the run id and its rejected row count."""
    run_id = conn.execute("INSERT INTO etl.load_run DEFAULT VALUES RETURNING run_id").fetchone()[0]
    log.info("Run %d: loading %s", run_id, ", ".join(files))
    try:
        # Raw tables, file records and the LOADED status commit together or not at all
        with conn.transaction():
            conn.execute("TRUNCATE raw.patients, raw.diagnoses, raw.labs, raw.encounters")
            for name, (table, columns) in FILES.items():
                rows = copy_into_raw(conn, files[name], table, columns)
                conn.execute(
                    "INSERT INTO etl.load_file (run_id, file_name, checksum, file_size, row_count)"
                    " VALUES (%s, %s, %s, %s, %s)",
                    (run_id, name, files[name].checksum, files[name].size, rows))
                log.info("Run %d: %s -> raw.%s, %d rows", run_id, name, table, rows)
            conn.execute("UPDATE etl.load_run SET status = 'LOADED' WHERE run_id = %s", (run_id,))

        # Separate transaction, so the raw data stays available for inspection if the merge fails
        conn.execute("CALL etl.process_run(%s::bigint)", (run_id,))
    except Exception as e:
        mark_failed(conn, run_id, e)
        raise

    rejects = conn.execute("SELECT count(*) FROM etl.load_reject WHERE run_id = %s", (run_id,)).fetchone()[0]
    return run_id, rejects


def mark_failed(conn: psycopg.Connection, run_id: int, error: Exception) -> None:
    try:
        conn.execute("""
            UPDATE etl.load_run SET status = 'FAILED', finished_at = now(), error_message = %s
            WHERE run_id = %s""", (f"{type(error).__name__}: {error}"[:4000], run_id))
    except psycopg.Error:
        log.exception("Run %d: could not record the failure", run_id)


def record_heartbeat(conn: psycopg.Connection, config: Config, outcome: str, detail: str) -> None:
    """Overwrites the single etl.heartbeat row, so the API can show when the ETL last checked."""
    try:
        conn.execute("""
            INSERT INTO etl.heartbeat (heartbeat_id, checked_at, outcome, detail, interval_seconds)
            VALUES (1, now(), %s, %s, %s)
            ON CONFLICT (heartbeat_id) DO UPDATE
            SET checked_at = EXCLUDED.checked_at, outcome = EXCLUDED.outcome,
                detail = EXCLUDED.detail, interval_seconds = EXCLUDED.interval_seconds""",
                     (outcome, detail or None, config.interval_seconds))
    except psycopg.Error as e:
        log.warning("Could not record the heartbeat: %s", str(e).strip())


def check_and_load(conn: psycopg.Connection, config: Config) -> tuple[str, str]:
    """Returns the outcome (waiting, busy, unchanged, loaded or failed) and a one-line detail."""
    files, waiting_detail = scan(config)
    if files is None:
        log.info("%s", waiting_detail)
        return "waiting", waiting_detail

    # Held until the connection closes at the end of the cycle
    if not conn.execute("SELECT pg_try_advisory_lock(%s)", (LOCK_KEY,)).fetchone()[0]:
        log.warning("Another ETL instance is running; skipping this cycle")
        return "busy", "Another ETL instance held the lock"

    close_abandoned_runs(conn)

    last_run_id, last_checksums = last_loaded_checksums(conn)
    if last_checksums == {name: f.checksum for name, f in files.items()}:
        log.info("Files unchanged since run %d; nothing to load", last_run_id)
        return "unchanged", f"Files unchanged since run {last_run_id}"

    try:
        run_id, rejects = load(conn, files)
    except Exception as e:
        log.exception("Load failed")
        return "failed", f"Load failed: {type(e).__name__}: {e}"[:1000]

    if rejects:
        log.warning("Run %d SUCCEEDED with %d rejected rows; see etl.load_reject", run_id, rejects)
    else:
        log.info("Run %d SUCCEEDED", run_id)
    return "loaded", f"Run {run_id} SUCCEEDED with {rejects} rejected rows"


def run_cycle(config: Config) -> str:
    """One check-and-load pass. Returns the outcome, for logging, retry timing and the exit code."""
    with psycopg.connect(config.conninfo, autocommit=True) as conn:
        conn.add_notice_handler(lambda notice: log.info("db: %s", notice.message_primary))

        if not schema_ready(conn):
            log.info("Schema not created yet (the clinical-data app runs the Flyway migrations)")
            return "not_ready"

        outcome, detail = check_and_load(conn, config)
        record_heartbeat(conn, config, outcome, detail)
        return outcome


def main(argv: list[str]) -> int:
    logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO"),
                        format="%(asctime)s %(levelname)s %(message)s")
    config = Config.from_env()
    once = "--once" in argv

    stop = threading.Event()
    for sig in (signal.SIGTERM, signal.SIGINT):
        signal.signal(sig, lambda *_: stop.set())

    log.info("Watching %s every %ds", config.data_dir, config.interval_seconds)
    while not stop.is_set():
        try:
            outcome = run_cycle(config)
        except psycopg.OperationalError as e:
            log.warning("Database unavailable: %s", str(e).strip())
            outcome = "db_down"
        except Exception:
            log.exception("Unexpected error")
            outcome = "failed"

        if once:
            return 1 if outcome in ("failed", "db_down", "not_ready") else 0
        stop.wait(RETRY_SECONDS if outcome in ("db_down", "not_ready") else config.interval_seconds)

    log.info("Stopped")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
