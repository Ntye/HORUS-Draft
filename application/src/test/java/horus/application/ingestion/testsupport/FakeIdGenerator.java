package horus.application.ingestion.testsupport;

import horus.application.port.IdGenerator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class FakeIdGenerator implements IdGenerator {

    private final AtomicLong counter = new AtomicLong();

    @Override
    public UUID newId() {
        return new UUID(0, counter.incrementAndGet());
    }
}
