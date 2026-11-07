package com.campuson.api;
class SchemaUpgradeTest extends SchemaUpgradeScenarios {
    @Override String url() { return "jdbc:h2:mem:upgrade_" + java.util.UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"; }
    @Override String username() { return "sa"; }
    @Override String password() { return ""; }
}
