--liquibase formatted sql

--changeset mcdodik:create-core-schema context:all
--comment:Создаём схему core
CREATE SCHEMA IF NOT EXISTS core;

--rollback DROP SCHEMA IF EXISTS core CASCADE;