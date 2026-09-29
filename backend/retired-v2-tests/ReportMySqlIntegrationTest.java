package com.dfwl.fleet.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TEST_MYSQL_JDBC_URL", matches = ".+")
class ReportMySqlIntegrationTest {

    @Test
    void mysqlSupportsReportAndSettlementDateSqlFragments() throws Exception {
        String url = System.getenv("TEST_MYSQL_JDBC_URL");
        String user = System.getenv("TEST_MYSQL_USER");
        String password = System.getenv("TEST_MYSQL_PASSWORD");
        assertThat(url).as("TEST_MYSQL_JDBC_URL").isNotBlank();
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            assertThat(statement.executeQuery("SELECT DATE_FORMAT(CURRENT_DATE, '%Y-%m')").next()).isTrue();
            assertThat(statement.executeQuery("SELECT YEAR(CURRENT_DATE)").next()).isTrue();
        }
    }
}
