package horus.application.port;

import java.util.List;

public interface ActiveListVersionLookup {

    // Ordered by list version id (I-4). Empty means nothing can be screened against.
    List<ActiveListVersion> findAllActive();
}
