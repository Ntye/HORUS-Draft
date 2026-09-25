package horus.application.port;

import horus.matching.WhitelistView;
import horus.normalisation.NormalisedName;

// Whitelist management is out of scope for this iteration (CLAUDE.md §12); the seam exists so the
// engine's flag-and-demote behaviour (I-8) is wired end to end. The bootstrap supplies an empty
// view until whitelist storage is built.
public interface WhitelistViewProvider {

    WhitelistView forQuery(NormalisedName query);
}
