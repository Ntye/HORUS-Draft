package horus.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WatchlistSchemaMigrationTest extends PostgresIntegrationSupport {

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "list_version", "watch_entity", "watch_entity_version",
            "watch_name", "watch_dob", "watch_identifier", "watch_country",
            "watch_address", "watch_link", "watch_external_source",
            "watch_designation", "watch_ownership",
            "audit_event", "config_version");

    @Test
    void migrationsCreateEveryExpectedTable() throws Exception {
        Set<String> actualTables = new HashSet<>();
        try (Connection connection = adminConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
            while (resultSet.next()) {
                actualTables.add(resultSet.getString("table_name"));
            }
        }

        assertThat(actualTables).containsAll(EXPECTED_TABLES);
    }
}
