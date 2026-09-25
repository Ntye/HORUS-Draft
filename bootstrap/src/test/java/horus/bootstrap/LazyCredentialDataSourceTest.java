package horus.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.adapter.persistence.MissingCredentialException;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LazyCredentialDataSourceTest {

    private static final String URL = "jdbc:postgresql://localhost:5432/horus";

    @Test
    void theCredentialIsNotReadUntilAConnectionIsRequested() {
        // The point of the class (CLAUDE.md §11): building the bean must not require the secret, so a
        // command that never touches this role does not need its password at all.
        AtomicInteger reads = new AtomicInteger();
        LazyCredentialDataSource dataSource =
                new LazyCredentialDataSource(URL, "horus_screen", () -> {
                    reads.incrementAndGet();
                    return "pw";
                });

        assertThat(reads).hasValue(0);
        assertThat(dataSource.initialised()).isFalse();
    }

    @Test
    void aMissingCredentialFailsAtFirstUseWithAnActionableMessageAndNoSecret() {
        // I-7: still loud. I-11: names the variable and the role, never a value.
        LazyCredentialDataSource dataSource = new LazyCredentialDataSource(
                URL, "horus_screen", () -> {
                    throw new MissingCredentialException("HORUS_SCREEN_PASSWORD", "horus_screen");
                });

        assertThatThrownBy(dataSource::getConnection)
                .isInstanceOf(MissingCredentialException.class)
                .hasMessageContaining("HORUS_SCREEN_PASSWORD")
                .hasMessageContaining("horus_screen")
                .hasMessageContaining("Set-LocalEnv.ps1")
                .hasMessageContaining("testing-guide.md");
    }

    @Test
    void theCredentialIsReadAtMostOnceEvenWhenConnectionsAreRequestedRepeatedly() {
        AtomicInteger reads = new AtomicInteger();
        LazyCredentialDataSource dataSource =
                new LazyCredentialDataSource(URL, "horus_screen", () -> {
                    reads.incrementAndGet();
                    return "pw";
                });

        // No database is running here, so each call fails to connect -- but the pool (and therefore the
        // credential read) is built exactly once regardless.
        for (int i = 0; i < 3; i++) {
            try {
                dataSource.getConnection();
            } catch (Exception expected) {
                // connection failure is irrelevant to this assertion
            }
        }

        assertThat(reads).hasValue(1);
        assertThat(dataSource.initialised()).isTrue();
    }

    @Test
    void constructionRejectsMissingArguments() {
        assertThatThrownBy(() -> new LazyCredentialDataSource(null, "horus_screen", () -> "pw"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LazyCredentialDataSource(URL, " ", () -> "pw"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LazyCredentialDataSource(URL, "horus_screen", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void closingBeforeFirstUseIsHarmless() throws Exception {
        new LazyCredentialDataSource(URL, "horus_screen", () -> "pw").close();
    }

    @Test
    void theExceptionCarriesTheVariableNameForProgrammaticHandling() {
        assertThat(new MissingCredentialException("HORUS_AUDIT_PASSWORD", "horus_audit").variableName())
                .isEqualTo("HORUS_AUDIT_PASSWORD");
    }
}
