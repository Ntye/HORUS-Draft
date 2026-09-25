package horus.application.ingestion.testsupport;

import horus.application.port.AuditEventPort;
import horus.domain.audit.AuditEvent;
import java.util.ArrayList;
import java.util.List;

public final class FakeAuditEventPort implements AuditEventPort {

    private final List<AuditEvent> recorded = new ArrayList<>();

    @Override
    public void record(AuditEvent event) {
        recorded.add(event);
    }

    public List<AuditEvent> recorded() {
        return List.copyOf(recorded);
    }
}
