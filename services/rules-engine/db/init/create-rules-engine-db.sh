#!/bin/sh
# Creates the rules-engine's database (rules_engine) and its own login in the shared PostgreSQL.
# Idempotent: compose runs it on every start, which also works on an existing data directory where
# /docker-entrypoint-initdb.d scripts never run again. The password is re-applied, so changing
# RULES_DB_PASSWORD in .env takes effect on the next start.
#
# Isolation: only superusers and the database owner may connect to rules_engine, and the rules-engine
# login cannot connect to the clinical data database.
set -eu

export PGPASSWORD="$POSTGRES_PASSWORD"
psql -v ON_ERROR_STOP=1 -h "${PGHOST:-postgres}" -U "$POSTGRES_USER" -d postgres \
    -v rules_user="$RULES_DB_USER" \
    -v rules_password="$RULES_DB_PASSWORD" \
    -v rules_db="$RULES_DB_NAME" \
    -v clinical_db="$DB_NAME" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN', :'rules_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'rules_user')
\gexec
ALTER ROLE :"rules_user" WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'rules_password';

SELECT format('CREATE DATABASE %I OWNER %I ENCODING ''UTF8''', :'rules_db', :'rules_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'rules_db')
\gexec

REVOKE CONNECT ON DATABASE :"rules_db" FROM PUBLIC;
REVOKE CONNECT ON DATABASE :"clinical_db" FROM PUBLIC;
SQL
echo "rules_engine database ready"
