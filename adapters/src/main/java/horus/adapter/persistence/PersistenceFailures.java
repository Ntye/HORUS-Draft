package horus.adapter.persistence;

import horus.application.port.RecordPersistenceException;
import java.sql.SQLException;
import java.util.regex.Pattern;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/**
 * Turns a database failure into a reason code the load report can print: the SQLSTATE plus the
 * constraint or {@code table.column} the server named, and nothing else.
 *
 * <p>Only schema identifiers survive. The server's message text is discarded on purpose -- for a
 * string-truncation error it contains the string, and here that is a name or an address (I-11).
 * Everything kept is matched against {@link #IDENTIFIER} first, so a driver that put something
 * unexpected in a field cannot smuggle it into a log.
 */
final class PersistenceFailures {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]{1,40}");
    private static final int MAX_LOCATION_LENGTH = 50;

    private PersistenceFailures() {
    }

    /**
     * Wraps {@code failure} so the load loop can quarantine one record and name the reason.
     * Always returns -- a failure that cannot be classified still gets a code, because "unknown"
     * is diagnosable and a swallowed exception is not.
     */
    static RecordPersistenceException translate(RuntimeException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException psql) {
                return new RecordPersistenceException(
                        "SQL_" + safe(psql.getSQLState(), "unknown") + locationOf(psql.getServerErrorMessage()),
                        failure);
            }
            if (cause instanceof SQLException sql) {
                return new RecordPersistenceException("SQL_" + safe(sql.getSQLState(), "unknown"), failure);
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return new RecordPersistenceException("SQL_unknown:" + safe(failure.getClass().getSimpleName(), "error"),
                failure);
    }

    // The constraint name if the server gave one -- it is the most specific thing available --
    // otherwise table.column. Both are schema identifiers, never data.
    private static String locationOf(ServerErrorMessage message) {
        if (message == null) {
            return "";
        }
        String constraint = safe(message.getConstraint(), null);
        if (constraint != null) {
            return ":" + truncate(constraint);
        }
        String table = safe(message.getTable(), null);
        String column = safe(message.getColumn(), null);
        if (table != null && column != null) {
            return ":" + truncate(table + "." + column);
        }
        if (table != null) {
            return ":" + truncate(table);
        }
        return "";
    }

    private static String safe(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        for (String part : value.split("\\.")) {
            if (!IDENTIFIER.matcher(part).matches()) {
                return fallback;
            }
        }
        return value;
    }

    private static String truncate(String value) {
        return value.length() <= MAX_LOCATION_LENGTH ? value : value.substring(0, MAX_LOCATION_LENGTH);
    }
}
