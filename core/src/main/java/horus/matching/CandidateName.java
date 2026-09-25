package horus.matching;

import horus.normalisation.NormalisedName;

public record CandidateName(NormalisedName name, boolean alias) {

    public CandidateName {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
    }
}
