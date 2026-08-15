package com.banksphere.kyc.integration;

import org.junit.jupiter.api.Assumptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Mirrors account-service's {@code PostgresAssumptions} exactly (see
 * ADR-004) — a real Postgres instance reachable at the same env vars
 * {@code application.yml} reads is required to prove a real optimistic-
 * lock conflict; if unreachable, the whole test class is skipped, not
 * failed.
 */
final class PostgresAssumptions {

    private static final Logger log = LoggerFactory.getLogger(PostgresAssumptions.class);

    private PostgresAssumptions() {
    }

    static void assumeReachable() {
        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "5432");
        String db = System.getenv().getOrDefault("DB_NAME", "banksphere_kyc");
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
