package horus.bootstrap;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;
import horus.adapter.persistence.MissingCredentialException;
import javax.sql.DataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.datasource.AbstractDataSource;

// A DataSource that reads its password and builds the real pool on FIRST USE, not at construction.
//
// Why (CLAUDE.md §11, segregation of duties): the composition root defines one DataSource per
// database role, and Spring creates them all at startup. When the password was injected with
// @Value, that made every role's credential mandatory for every command -- an operator running
// `ingest` had to hold the horus_screen password, which is exactly the separation the three roles
// exist to enforce. Deferring the read means a command only needs the roles it actually touches.
//
// This does NOT weaken I-7: a missing credential still fails loudly and un-ignorably, with an
// actionable message (MissingCredentialException), at the moment the role is first used. A wrong
// credential already only failed at connection time.
//
// The delegate is created at most once. `close()` is Spring's inferred destroy method, so the pool
// is released with the context.
final class LazyCredentialDataSource extends AbstractDataSource implements AutoCloseable {

    private final String url;
    private final String username;
    private final Supplier<String> password;

    private volatile DataSource delegate;

    LazyCredentialDataSource(String url, String username, Supplier<String> password) {
        if (url == null || url.isBlank() || username == null || username.isBlank() || password == null) {
            throw new IllegalArgumentException("url, username and password supplier are required");
        }
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return delegate().getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return delegate().getConnection(username, password);
    }

    /** True once the credential has been read and the pool built -- used by tests to prove laziness. */
    boolean initialised() {
        return delegate != null;
    }

    private DataSource delegate() {
        DataSource existing = delegate;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (delegate == null) {
                // password.get() throws MissingCredentialException when the variable is absent.
                delegate = DataSourceBuilder.create()
                        .url(url)
                        .username(username)
                        .password(password.get())
                        .build();
            }
            return delegate;
        }
    }

    @Override
    public void close() throws Exception {
        DataSource current = delegate;
        if (current instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
}
