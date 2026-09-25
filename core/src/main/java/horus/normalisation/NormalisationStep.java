package horus.normalisation;

public interface NormalisationStep {

    String stepId();

    String apply(String input);
}
