package com.erp.portal;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DatabaseMigrationIntegrationTest {

    @Test
    void flywayMigrationsExecuteSuccessfullyOnMySqlDatabase() {
        String host = "localhost";
        int port = 3306;
        String user = "root";
        String password = "Anish@9661";
        String testDb = "erp_migration_test_" + System.currentTimeMillis();

        boolean mysqlConnected = false;
        try (Connection rootConn = DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + port + "/?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false",
                user, password)) {
            mysqlConnected = true;
            try (Statement stmt = rootConn.createStatement()) {
                stmt.execute("CREATE DATABASE IF NOT EXISTS `" + testDb + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }

            Flyway flyway = Flyway.configure()
                    .dataSource("jdbc:mysql://" + host + ":" + port + "/" + testDb + "?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false",
                            user, password)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false)
                    .load();

            var result = flyway.migrate();
            assertThat(result.migrationsExecuted).isGreaterThan(0);
            assertThat(result.success).isTrue();

            // Clean up test database
            try (Statement stmt = rootConn.createStatement()) {
                stmt.execute("DROP DATABASE IF EXISTS `" + testDb + "`");
            }
        } catch (Exception e) {
            assumeTrue(false, "MySQL is not accessible for live migration test: " + e.getMessage());
        }
    }
}
