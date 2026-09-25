package horus.application.port;

import horus.domain.audit.AuditEvent;

// I-5: append-only. The adapter behind this holds only the horus_audit credential, which the
// database restricts to INSERT and SELECT (proven in Step 2's AuditEventPrivilegesTest).
public interface AuditEventPort {

    void record(AuditEvent event);
}
