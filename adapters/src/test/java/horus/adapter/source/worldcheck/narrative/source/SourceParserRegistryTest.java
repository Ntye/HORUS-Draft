package horus.adapter.source.worldcheck.narrative.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceParserRegistryTest {

    @Test
    void resolvesToTheFirstMatchingSpecificParser() {
        SourceParserRegistry registry = SourceParserRegistry.standard();

        assertThat(registry.resolve("USA SANCTIONS - OFAC").parserId()).isEqualTo("OFAC");
        assertThat(registry.resolve("UK SANCTIONS - UKHMT").parserId()).isEqualTo("OFSI");
        assertThat(registry.resolve("EU SANCTIONS").parserId()).isEqualTo("EU");
    }

    @Test
    void fallsBackToGenericForAnUnrecognisedAuthority() {
        SourceParserRegistry registry = SourceParserRegistry.standard();

        assertThat(registry.resolve("SOME OTHER AUTHORITY").parserId()).isEqualTo("GENERIC");
    }

    @Test
    void tracksResolutionCountsPerParser() {
        SourceParserRegistry registry = SourceParserRegistry.standard();

        registry.resolve("USA SANCTIONS - OFAC");
        registry.resolve("USA SANCTIONS - OFAC");
        registry.resolve("SOME OTHER AUTHORITY");

        assertThat(registry.resolutionCounts()).containsEntry("OFAC", 2L);
        assertThat(registry.resolutionCounts()).containsEntry("GENERIC", 1L);
    }

    @Test
    void parserIdsIncludesEveryRegisteredParserAndTheFallback() {
        SourceParserRegistry registry = SourceParserRegistry.standard();

        assertThat(registry.parserIds()).containsExactly("OFSI", "OFAC", "EU", "GENERIC");
    }
}
