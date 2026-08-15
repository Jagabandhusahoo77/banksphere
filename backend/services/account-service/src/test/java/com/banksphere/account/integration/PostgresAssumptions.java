package com.banksphere.account.integration;

import org.junit.jupiter.api.Assumptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * These real-PostgreSQL integration tests need a live database reachable
 * at the same {@code DB_HOST}/{@code DB_PORT}/{@code DB_NAME}/
 * {@code DB_USERNAME}/{@code DB_PASSWORD} env vars (same names, same
 * localhost defaults) that {@code application.yml} already reads for the
 * real service — see docs/architecture/decisions/ADR-004 for why a real
 * database, not Mockito, is required to prove a real transaction
 * rollback. If it isn't reachable, the whole test class is skipped (not
 * failed) via {@link Assumptions#assumeTrue}, so {@code mvn verify}
 * degrades gracefully in an environment with no Postgres running rather
 * than reporting a false build failure.
 */
final class PostgresAssumptions {

    private static final Logger log = LoggerFactory.getLogger(PostgresAssumptions.class);

    private PostgresAssumptions() {
    }

    static void assumeReachable() {
        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "5432");
        String db = System.getenv().getOrDefault("DB_NAME", "banksphere_account");
        String username = System.getenv().getOrDefault("DB_USERNAME", "banksphere");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "banksphere_local_dev");
        String url = "jdbc:postgresql://%s:%s/%s?connectTimeout=2".formatted(host, port, db);

        boolean reachable;
        try (Connection ignored = DriverManager.getConnection(url, username, password)) {
            reachable = true;
        } catch (SQLException ex) {
            reachable = false;
            log.warn("Postgres not reachable at {} — skipping real-database integration test ({})", url, ex.getMessage());
        }

        Assumptions.assumeTrue(reachable, () -> "Postgres not reachable at " + url + " — skipping real-database integration test");
    }
}
