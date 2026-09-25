package horus.ingestion.spi;

import horus.domain.shared.CanonicalSlot;
import horus.ingestion.format.RawRecord;
import java.util.Set;

// SEMANTICS LIVE HERE, never in configuration (horus-ingestion-spi.drawio) -- one adapter per
// watchlist source.
public interface WatchlistSourceAdapter {

    String sourceId();

    String formatId();

    CanonicalRecord toCanonical(RawRecord r);

    CapabilityExpectation expectation();

    Set<CanonicalSlot> requiredSlots();
}
