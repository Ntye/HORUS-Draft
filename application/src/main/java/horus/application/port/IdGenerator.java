package horus.application.port;

import java.util.UUID;

// I-4: core and application never call UUID.randomUUID() directly -- ids are injected so a
// screening or a load is reproducible given the same inputs and the same generator sequence.
public interface IdGenerator {

    UUID newId();
}
