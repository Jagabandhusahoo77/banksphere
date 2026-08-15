#!/bin/sh
# BankSphere uses a database-per-service model on a single local Postgres
# instance. POSTGRES_DB (see docker-compose.yml) creates the first
# database; this script creates the remaining ones, owned by whichever role
# was actually configured via POSTGRES_USER (never hard-coded), so this
# still works if DB_USERNAME is customized away from the "banksphere" default.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE banksphere_account OWNER "$POSTGRES_USER";
    CREATE DATABASE banksphere_transaction OWNER "$POSTGRES_USER";
    CREATE DATABASE banksphere_beneficiary OWNER "$POSTGRES_USER";
    CREATE DATABASE banksphere_employee OWNER "$POSTGRES_USER";
    CREATE DATABASE banksphere_kyc OWNER "$POSTGRES_USER";
EOSQL
