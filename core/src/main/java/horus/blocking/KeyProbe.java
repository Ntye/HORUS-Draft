package horus.blocking;

import horus.normalisation.NormalisedName;

// What one strategy is asked to look up. The name was produced by the shared NormalisationPipeline
// (I-10), so the probe is symmetric with what ingestion stored.
public record KeyProbe(Strategy strategy, NormalisedName name) {

    public KeyProbe {
        if (strategy == null || name == null) {
            throw new IllegalArgumentException("strategy and name must not be null");
        }
    }
}
