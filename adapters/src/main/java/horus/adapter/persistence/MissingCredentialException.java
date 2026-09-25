package horus.adapter.persistence;

// A required database credential is absent from the environment. Thrown when the credential is first
// needed, not at boot, so a command only requires the roles it actually uses (CLAUDE.md §11: an
// operator running an ingest must not need the screening role's password).
//
// It lives in the adapter ring, not in bootstrap, because two rings must recognise it and adapters
// may not import bootstrap (§4): bootstrap's DataSourceConfig throws it, and the CLI has to tell a
// missing-credential failure apart from a screening that genuinely could not be completed.
//
// I-11: the message names the ENVIRONMENT VARIABLE and the role, never a value. Nothing here may
// interpolate a secret.
public final class MissingCredentialException extends IllegalStateException {

    private final String variableName;

    public MissingCredentialException(String variableName, String role) {
        super(variableName + " is not set, and it is required to connect as the database role '" + role
                + "'.\n"
                + "  Locally:  . .\\scripts\\Set-LocalEnv.ps1     (note the leading dot)\n"
                + "  In production the four HORUS_*_PASSWORD values come from the secrets manager.\n"
                + "  See docs/runbook/testing-guide.md §2.");
        this.variableName = variableName;
    }

    public String variableName() {
        return variableName;
    }

    /**
     * Finds a missing-credential failure anywhere in a cause chain, or null. Spring wraps it -- a
     * JdbcTemplate call surfaces it as CannotGetJdbcConnectionException -- so callers that need to
     * tell configuration faults from data faults must look at the causes, not the thrown type.
     */
    public static MissingCredentialException findIn(Throwable thrown) {
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            if (cause instanceof MissingCredentialException missing) {
                return missing;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return null;
    }
}
