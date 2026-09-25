package horus.adapter.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;

// Singleton container: started once for the whole test JVM, not once per test class, so the
// three test classes here don't each pay for their own Postgres startup + migration run.
public abstract class PostgresIntegrationSupport {

    public static final String INGEST_PASSWORD = "test-ingest-pw";
    public static final String SCREEN_PASSWORD = "test-screen-pw";
    public static final String AUDIT_PASSWORD = "test-audit-pw";

    private static final PostgreSQLContainer<?> CONTAINER = new PostgreSQLContainer<>("postgres:16");

    static {
        CONTAINER.start();
        try (Connection admin = adminConnection();
                Statement statement = admin.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            statement.execute("CREATE EXTENSION IF NOT EXISTS unaccent");
            statement.execute("CREATE ROLE horus_ingest LOGIN PASSWORD '" + INGEST_PASSWORD + "'");
            statement.execute("CREATE ROLE horus_screen LOGIN PASSWORD '" + SCREEN_PASSWORD + "'");
            statement.execute("CREATE ROLE horus_audit LOGIN PASSWORD '" + AUDIT_PASSWORD + "'");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to prepare the test database", e);
        }

        Flyway.configure()
                .dataSource(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword())
                .load()
                .migrate();
    }

    public static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(
                CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
    }

    public static Connection connectAs(String username, String password) throws SQLException {
        return DriverManager.getConnection(CONTAINER.getJdbcUrl(), username, password);
    }
}
