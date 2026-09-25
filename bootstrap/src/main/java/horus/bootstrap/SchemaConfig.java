package horus.bootstrap;

import horus.adapter.persistence.SchemaMigrator;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// The schema is migrated during context refresh, before any command runs, on the admin credential
// only. A failure here stops the application from starting (I-7).
@Configuration
public class SchemaConfig {

    @Bean
    public SchemaMigrator schemaMigrator(@Qualifier("adminDataSource") DataSource adminDataSource) {
        SchemaMigrator migrator = new SchemaMigrator(adminDataSource);
        migrator.migrate();
        return migrator;
    }
}
