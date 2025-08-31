--liquibase formatted sql

--changeset mcdodik:enable-pg_stat_statements
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

--rollback DROP EXTENSION IF EXISTS pg_stat_statements;
