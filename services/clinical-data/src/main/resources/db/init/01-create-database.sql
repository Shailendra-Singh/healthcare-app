-- Bootstraps the clinical_data database.
--
-- Flyway connects to an existing database, so this cannot be a Flyway migration.
-- Run once against the server's maintenance database (e.g. "postgres") with psql,
-- or mount into /docker-entrypoint-initdb.d for the official Postgres image:
--
--   psql -h localhost -U postgres -d postgres -f 01-create-database.sql
--
-- PostgreSQL has no CREATE DATABASE IF NOT EXISTS; \gexec makes this idempotent.

SELECT 'CREATE DATABASE clinical_data ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'clinical_data')
\gexec
