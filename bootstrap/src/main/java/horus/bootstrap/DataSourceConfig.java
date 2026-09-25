package horus.bootstrap;

import horus.adapter.persistence.MissingCredentialException;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

// CLAUDE.md §11: three DB roles, each handed only to its own repository -- segregation of duties is
// enforced by which credential a repository holds, not by code discipline. This is the ONLY place any
// of these passwords are read (CLAUDE.md §3/§9).
//
// Passwords are resolved from the Environment on FIRST USE, not injected with @Value. With @Value,
// Spring resolved all four placeholders while building the context, so every command required every
// role's password: running `ingest` failed with "Could not resolve placeholder
// 'HORUS_SCREEN_PASSWORD'" under ~200 stack frames. That both leaked the screening credential into
// the ingest operator's environment and buried the cause. See LazyCredentialDataSource.
//
// horus_admin is the exception and stays effectively mandatory: SchemaConfig migrates during context
// refresh, so its credential is always used. That is deliberate -- nothing should run against an
// unmigrated schema (I-7).
@Configuration
public class DataSourceConfig {

    private final Environment environment;
    private final String host;
    private final int port;
    private final String database;

    public DataSourceConfig(
            Environment environment,
            @Value("${HORUS_DB_HOST:localhost}") String host,
            @Value("${HORUS_DB_PORT:5432}") int port,
            @Value("${HORUS_DB_NAME:horus}") String database) {
        this.environment = environment;
        this.host = host;
        this.port = port;
        this.database = database;
    }

    // Deliberately NOT @Primary. It used to be, so that Flyway's autoconfiguration would migrate with
    // DDL rights -- but that autoconfiguration is not on the classpath (SchemaConfig calls the migrator
    // explicitly), and @Primary silently made the admin credential the answer to every unqualified
    // DataSource injection. That is how all three JdbcTemplates below ended up on horus_admin.
    // With no primary, an unqualified injection fails at startup instead of quietly escalating
    // privilege -- which is the behaviour a segregation-of-duties boundary should have (§11).
    @Bean
    public DataSource adminDataSource() {
        return build("horus_admin", "HORUS_DB_ADMIN_PASSWORD");
    }

    @Bean
    public DataSource ingestDataSource() {
        return build("horus_ingest", "HORUS_INGEST_PASSWORD");
    }

    @Bean
    public DataSource screenDataSource() {
        return build("horus_screen", "HORUS_SCREEN_PASSWORD");
    }

    @Bean
    public DataSource auditDataSource() {
        return build("horus_audit", "HORUS_AUDIT_PASSWORD");
    }

    // @Qualifier is mandatory on every one of these, and is the whole point of the class. Relying on
    // the parameter NAME to pick among several DataSource beans does not work: Spring resolves by type
    // first, and a @Primary candidate outranks parameter-name matching. When adminDataSource was
    // @Primary, all three of these templates wrapped it, so every repository ran as horus_admin and
    // the three roles existed only in the GRANTs. JdbcTemplateRoleWiringTest asserts the connections.
    @Bean
    public JdbcTemplate ingestJdbcTemplate(@Qualifier("ingestDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public JdbcTemplate screenJdbcTemplate(@Qualifier("screenDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public JdbcTemplate auditJdbcTemplate(@Qualifier("auditDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    private DataSource build(String role, String passwordVariable) {
        return new LazyCredentialDataSource(
                // reWriteBatchedInserts turns a JDBC batch into one multi-row INSERT rather than
                // N single-row statements sharing a prepare. Without it, batching the child
                // tables saves the prepares but not the per-row protocol traffic, which is most
                // of what made the real load run at 34 records/second.
                "jdbc:postgresql://" + host + ":" + port + "/" + database
                        + "?reWriteBatchedInserts=true",
                role,
                () -> requirePassword(role, passwordVariable));
    }

    // I-11: only the variable's NAME is ever put in a message, never its value.
    private String requirePassword(String role, String passwordVariable) {
        String password = environment.getProperty(passwordVariable);
        if (password == null || password.isBlank()) {
            throw new MissingCredentialException(passwordVariable, role);
        }
        return password;
    }
}
