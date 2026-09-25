package horus.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

// §11: "Three DB roles, each handed only to its own repository by bootstrap. Segregation of duties is
// enforced by which credential each repository holds, not by code discipline."
//
// ApplicationBootTest asserts the four DataSource BEANS connect as their own roles, which they always
// did. It could not catch the defect this class exists for: the JdbcTemplate beans were declared as
// `JdbcTemplate ingestJdbcTemplate(DataSource ingestDataSource)`, relying on the PARAMETER NAME to
// select among four DataSource beans -- but @Primary (adminDataSource, needed so Flyway migrates with
// DDL rights) outranks parameter-name matching. Every repository therefore ran as horus_admin, and
// the role separation existed only in the GRANTs, never in the connections.
//
// These assertions are on what the repositories actually hold, so they fail if that ever regresses.
class JdbcTemplateRoleWiringTest {

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

    private static ConfigurableApplicationContext boot() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("HORUS_DB_HOST", postgres.getHost());
        properties.put("HORUS_DB_PORT", postgres.getMappedPort(5432));
        properties.put("HORUS_DB_NAME", postgres.getDatabaseName());
        properties.put("HORUS_DB_ADMIN_PASSWORD", "admin-pw");
        properties.put("HORUS_INGEST_PASSWORD", "ingest-pw");
        properties.put("HORUS_SCREEN_PASSWORD", "screen-pw");
        properties.put("HORUS_AUDIT_PASSWORD", "audit-pw");
        SpringApplication application = new SpringApplication(HorusApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(properties);
        return application.run("--help");
    }

    @Test
    void eachJdbcTemplateConnectsAsItsOwnRoleNotAsTheAdminRole() {
        try (ConfigurableApplicationContext context = boot()) {
            assertThat(userOf(context, "ingestJdbcTemplate")).isEqualTo("horus_ingest");
            assertThat(userOf(context, "screenJdbcTemplate")).isEqualTo("horus_screen");
            assertThat(userOf(context, "auditJdbcTemplate")).isEqualTo("horus_audit");
        }
    }

    private static String userOf(ConfigurableApplicationContext context, String jdbcTemplateBean) {
        return context.getBean(jdbcTemplateBean, JdbcTemplate.class)
                .queryForObject("SELECT current_user", String.class);
    }
}
