package horus.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.adapter.persistence.MissingCredentialException;

import horus.adapter.cli.MeasureBlockingCommand;
import horus.adapter.cli.PublishConfigCommand;
import horus.adapter.cli.ScreenCommand;
import horus.adapter.cli.ShowScreeningCommand;
import horus.application.ingestion.LoadWatchlistVersion;
import horus.application.screening.ScreenAName;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

// Every other integration test builds its adapters by hand, so none of them can see a wiring
// defect. This boots the REAL composition root against a real Postgres. It exists because in
// Step 9 the application was found unable to start at all: Spring proxies @Repository beans for
// exception translation, and the persistence adapters were `final` classes it cannot subclass.
class ApplicationBootTest {

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startDatabase() throws Exception {
        postgres = new PostgreSQLContainer<>("postgres:16");
        postgres.start();
        try (Connection connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            statement.execute("CREATE EXTENSION IF NOT EXISTS unaccent");
            statement.execute("CREATE ROLE horus_admin SUPERUSER LOGIN PASSWORD 'admin-pw'");
            statement.execute("CREATE ROLE horus_ingest LOGIN PASSWORD 'ingest-pw'");
            statement.execute("CREATE ROLE horus_screen LOGIN PASSWORD 'screen-pw'");
            statement.execute("CREATE ROLE horus_audit LOGIN PASSWORD 'audit-pw'");
        }
    }

    @AfterAll
    static void stopDatabase() {
        postgres.stop();
    }

    private static Map<String, Object> properties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("HORUS_DB_HOST", postgres.getHost());
        properties.put("HORUS_DB_PORT", postgres.getMappedPort(5432));
        properties.put("HORUS_DB_NAME", postgres.getDatabaseName());
        properties.put("HORUS_DB_ADMIN_PASSWORD", "admin-pw");
        properties.put("HORUS_INGEST_PASSWORD", "ingest-pw");
        properties.put("HORUS_SCREEN_PASSWORD", "screen-pw");
        properties.put("HORUS_AUDIT_PASSWORD", "audit-pw");
        return properties;
    }

    // "--help" makes the picocli runner print usage and return 0 without needing any data.
    private static ConfigurableApplicationContext boot(Map<String, Object> properties) {
        SpringApplication application = new SpringApplication(HorusApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(properties);
        return application.run("--help");
    }

    @Test
    void theWholeApplicationBootsAndEachDataSourceConnectsAsItsOwnRole() {
        try (ConfigurableApplicationContext context = boot(properties())) {
            // Segregation of duties (§11): each repository holds only its own role's credential.
            assertThat(currentUser(context, "ingestDataSource")).isEqualTo("horus_ingest");
            assertThat(currentUser(context, "screenDataSource")).isEqualTo("horus_screen");
            assertThat(currentUser(context, "auditDataSource")).isEqualTo("horus_audit");
            assertThat(currentUser(context, "adminDataSource")).isEqualTo("horus_admin");

            // The use cases and every command are wired.
            assertThat(context.getBean(LoadWatchlistVersion.class)).isNotNull();
            assertThat(context.getBean(ScreenAName.class)).isNotNull();
            assertThat(context.getBean(ScreenCommand.class)).isNotNull();
            assertThat(context.getBean(ShowScreeningCommand.class)).isNotNull();
            assertThat(context.getBean(PublishConfigCommand.class)).isNotNull();
            assertThat(context.getBean(MeasureBlockingCommand.class)).isNotNull();

            // Flyway ran on the admin credential: the schema, views and grants exist.
            JdbcTemplate admin = new JdbcTemplate(context.getBean("adminDataSource", DataSource.class));
            assertThat(admin.queryForObject(
                            "SELECT COUNT(*) FROM flyway_schema_history WHERE success", Integer.class))
                    .isGreaterThanOrEqualTo(7);
        }
    }

    @Test
    void theApplicationBootsWithoutTheScreeningCredentialSoAnIngestOperatorDoesNotNeedIt() {
        // Regression for the defect an operator hit running a real ingest: every role's password was
        // resolved while building the context, so `ingest` died on "Could not resolve placeholder
        // 'HORUS_SCREEN_PASSWORD'". §11 -- an ingest operator must not need the screening credential.
        Map<String, Object> withoutScreen = properties();
        withoutScreen.remove("HORUS_SCREEN_PASSWORD");

        try (ConfigurableApplicationContext context = boot(withoutScreen)) {
            // The ingest path is fully usable.
            assertThat(context.getBean(LoadWatchlistVersion.class)).isNotNull();
            assertThat(currentUser(context, "ingestDataSource")).isEqualTo("horus_ingest");
            assertThat(currentUser(context, "auditDataSource")).isEqualTo("horus_audit");

            // I-7: using the screening role still fails loudly, and says exactly what to set.
            DataSource screen = context.getBean("screenDataSource", DataSource.class);
            assertThatThrownBy(screen::getConnection)
                    .isInstanceOf(MissingCredentialException.class)
                    .hasMessageContaining("HORUS_SCREEN_PASSWORD");
        }
    }

    private static String currentUser(ConfigurableApplicationContext context, String dataSourceBean) {
        return new JdbcTemplate(context.getBean(dataSourceBean, DataSource.class))
                .queryForObject("SELECT current_user", String.class);
    }
}
