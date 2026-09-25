package horus.application.screening;

// The seam the benchmark harness drives: it runs every labelled case through the SAME use case a
// consumer would (spec §19.3), so what is measured is what ships -- not a test-only code path.
public interface Screener {

    ScreenAName.Result execute(ScreenAName.Command command);
}
