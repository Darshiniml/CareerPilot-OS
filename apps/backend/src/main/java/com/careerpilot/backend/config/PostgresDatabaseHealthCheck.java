package com.careerpilot.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
@Profile("!test")
@Slf4j
public class PostgresDatabaseHealthCheck implements CommandLineRunner {

    private final DataSource dataSource;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    public PostgresDatabaseHealthCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("[DB-CHECK] Verifying PostgreSQL connection for runtime profile...");
        try (Connection conn = dataSource.getConnection()) {
            String dbProductName = conn.getMetaData().getDatabaseProductName();
            log.info("[DB-CHECK] Successfully connected to database: {} ({})", dbProductName, dbUrl);
            if (!dbProductName.toLowerCase().contains("postgresql")) {
                log.warn("[DB-CHECK] Active database engine is {} instead of PostgreSQL.", dbProductName);
            }
        } catch (Exception e) {
            String errMsg = "\n========================================================================\n" +
                    "CRITICAL DATABASE ERROR: CareerPilot PostgreSQL database is unavailable.\n" +
                    "URL attempted: " + dbUrl + "\n" +
                    "Error: " + e.getMessage() + "\n" +
                    "Action Required: Start local PostgreSQL on port 5432 or configure environment variables:\n" +
                    "  - DB_HOST (default: localhost)\n" +
                    "  - DB_PORT (default: 5432)\n" +
                    "  - DB_NAME (default: careerpilot)\n" +
                    "  - DB_USER (default: postgres)\n" +
                    "  - DB_PASSWORD (default: postgres)\n" +
                    "========================================================================";
            log.error(errMsg);
            throw new IllegalStateException("CareerPilot PostgreSQL database is unavailable. Start PostgreSQL or configure DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD.", e);
        }
    }
}
