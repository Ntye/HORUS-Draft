package horus.application.ingestion.testsupport;

import horus.application.port.IndexReconciliation;
import horus.domain.shared.ListVersionId;
import java.util.HashSet;
import java.util.Set;

public final class FakeIndexReconciliation implements IndexReconciliation {

    private boolean completeByDefault = true;
    private final Set<ListVersionId> forcedIncomplete = new HashSet<>();

    public void forceIncomplete(ListVersionId listVersionId) {
        forcedIncomplete.add(listVersionId);
    }

    public void forceAllIncomplete() {
        completeByDefault = false;
    }

    @Override
    public boolean isComplete(ListVersionId listVersionId) {
        return completeByDefault && !forcedIncomplete.contains(listVersionId);
    }
}
