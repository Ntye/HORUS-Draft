package horus.adapter.persistence;

import horus.application.port.IdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Component;

// I-4: the one place UUID.randomUUID() is called. Everything inward (core, application) only
// ever sees this through the IdGenerator port.
@Component
public final class SystemIdGenerator implements IdGenerator {

    @Override
    public UUID newId() {
        return UUID.randomUUID();
    }
}
