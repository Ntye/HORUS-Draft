package horus.adapter.source.worldcheck.narrative;

import static org.assertj.core.api.Assertions.assertThat;

import horus.domain.shared.Provenance;
import horus.domain.watchentity.ActionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatusDeriverTest {

    private static Facts.SanctionEvent event(String source, ActionType type, int year) {
        return new Facts.SanctionEvent(source, type, type.name().toLowerCase(), year,
                new Provenance("TEST.RULE", 0, 10));
    }

    @Test
    void noSanctionsSectionAtAllIsNotSanctioned() {
        Facts facts = new Facts(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0, 0);

        var result = StatusDeriver.derive(facts);

        assertThat(result.status()).isEqualTo(StatusDeriver.RecordStatus.NOT_SANCTIONED);
    }

    @Test
    void removedEverywhereWhenEverySourcesLatestEventIsARemovalAfterItsAddition() {
        Facts facts = new Facts(List.of(), List.of(
                event("A:SOURCE", ActionType.ADDITION, 2000),
                event("A:SOURCE", ActionType.REMOVAL, 2015),
                event("B:SOURCE", ActionType.ADDITION, 2001),
                event("B:SOURCE", ActionType.REMOVAL, 2010)),
                List.of(), List.of(), List.of(), List.of(), 2, 2);

        var result = StatusDeriver.derive(facts);

        assertThat(result.status()).isEqualTo(StatusDeriver.RecordStatus.REMOVED_EVERYWHERE);
        assertThat(StatusDeriver.mayStillBeDesignated(result)).isFalse();
    }

    @Test
    void liveWhenAnySourceStillDesignatesEvenIfAnotherHasRemoved() {
        Facts facts = new Facts(List.of(), List.of(
                event("A:SOURCE", ActionType.ADDITION, 2022),
                event("B:SOURCE", ActionType.ADDITION, 2015),
                event("B:SOURCE", ActionType.REMOVAL, 2016)),
                List.of(), List.of(), List.of(), List.of(), 2, 2);

        var result = StatusDeriver.derive(facts);

        assertThat(result.status()).isEqualTo(StatusDeriver.RecordStatus.LIVE);
        assertThat(StatusDeriver.mayStillBeDesignated(result)).isTrue();
    }

    @Test
    void aGeneralLicenceAloneImpliesALiveListingAndMovesNoAdditionDate() {
        Facts facts = new Facts(List.of(), List.of(
                event("A:SOURCE", ActionType.AUTHORISATION, 2023)),
                List.of(), List.of(), List.of(), List.of(), 1, 1);

        var result = StatusDeriver.derive(facts);

        assertThat(result.status()).isEqualTo(StatusDeriver.RecordStatus.LIVE);
        assertThat(result.perSource()).allSatisfy(s -> assertThat(s.lastAdditionYear()).isNull());
    }

    @Test
    void unclassifiedEventsAloneAreIndeterminateAndTreatedAsMayStillBeDesignated() {
        Facts facts = new Facts(List.of(), List.of(
                event("A:SOURCE", ActionType.UNCLASSIFIED, 2020)),
                List.of(), List.of(), List.of(), List.of(), 1, 1);

        var result = StatusDeriver.derive(facts);

        assertThat(result.status()).isEqualTo(StatusDeriver.RecordStatus.INDETERMINATE);
        assertThat(StatusDeriver.mayStillBeDesignated(result)).isTrue();
    }
}
