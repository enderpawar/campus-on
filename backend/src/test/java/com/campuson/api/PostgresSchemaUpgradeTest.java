package com.campuson.api;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresSchemaUpgradeTest extends SchemaUpgradeScenarios {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @Override String url() {
        String schema="upgrade_"+java.util.UUID.randomUUID().toString().replace("-", "");
        try (var c=java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), username(), password()); var s=c.createStatement()) {
            s.execute("CREATE SCHEMA " + schema);
        } catch (java.sql.SQLException ex) { throw new IllegalStateException(ex); }
        return postgres.getJdbcUrl()+(postgres.getJdbcUrl().contains("?") ? "&" : "?")+"currentSchema="+schema;
    }
    @Override String username() { return postgres.getUsername(); }
    @Override String password() { return postgres.getPassword(); }
}
