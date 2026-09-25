package horus.adapter.persistence;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

// Applies the Flyway migrations in classpath:db/migration. Deliberately explicit: Spring Boot 4
// moved Flyway auto-configuration into a separate starter, and without it the application starts
// against an empty database and every later query fails. Migrating is DDL, so the caller hands in
// the admin DataSource -- the only credential that may (CLAUDE.md §11). A failed migration throws
// and fails the boot (I-7); nothing screens or ingests against a half-migrated schema.
public final class SchemaMigrator {

    private final DataSource adminDataSource;

    public SchemaMigrator(DataSource adminDataSource) {
        if (adminDataSource == null) {
            throw new IllegalArgumentException("adminDataSource must not be null");
        }
        this.adminDataSource = adminDataSource;
    }

    /** Returns the number of migrations applied by this call (0 when already up to date). */
    public int migrate() {
        return Flyway.configure()
                .dataSource(adminDataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()
                .migrationsExecuted;
    }
}
