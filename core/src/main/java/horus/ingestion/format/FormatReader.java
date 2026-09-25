package horus.ingestion.format;

import java.io.InputStream;
import java.util.Iterator;

// Semantics-free: knows nothing about watchlists, only how to turn one file shape into a
// stream of RawRecords. Meaning lives entirely in horus.ingestion.spi's WatchlistSourceAdapter.
public interface FormatReader {

    String formatId();

    Iterator<RawRecord> open(InputStream stream);
}
