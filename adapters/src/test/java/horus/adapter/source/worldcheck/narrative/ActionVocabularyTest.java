package horus.adapter.source.worldcheck.narrative;

import static org.assertj.core.api.Assertions.assertThat;

import horus.domain.watchentity.ActionType;
import org.junit.jupiter.api.Test;

class ActionVocabularyTest {

    @Test
    void classifiesTheLifecycleVerbs() {
        assertThat(ActionVocabulary.classify("removed").type()).isEqualTo(ActionType.REMOVAL);
        assertThat(ActionVocabulary.classify("designated").type()).isEqualTo(ActionType.ADDITION);
        assertThat(ActionVocabulary.classify("amended").type()).isEqualTo(ActionType.AMENDMENT);
        assertThat(ActionVocabulary.classify("fined").type()).isEqualTo(ActionType.WARNING);
    }

    @Test
    void unrecognisedVerbIsUnclassifiedNeverGuessed() {
        var c = ActionVocabulary.classify("did something entirely novel");

        assertThat(c.type()).isEqualTo(ActionType.UNCLASSIFIED);
        assertThat(c.matchedPhrase()).isNull();
    }

    @Test
    void v3PreviouslyUnclassifiedVerbsAreNowClassified() {
        assertThat(ActionVocabulary.classify("imposed").type()).isNotEqualTo(ActionType.UNCLASSIFIED);
        assertThat(ActionVocabulary.classify("no longer applies").type())
                .isNotEqualTo(ActionType.UNCLASSIFIED);
        assertThat(ActionVocabulary.classify("not in effect").type())
                .isNotEqualTo(ActionType.UNCLASSIFIED);
        assertThat(ActionVocabulary.classify("list officially confirmed").type())
                .isNotEqualTo(ActionType.UNCLASSIFIED);
    }

    @Test
    void v4GeneralLicencesAreAuthorisationNeverALifecycleEvent() {
        assertThat(ActionVocabulary.classify("ofac issued general license no").type())
                .isEqualTo(ActionType.AUTHORISATION);
        assertThat(ActionVocabulary.classify("ofsi general licence int/").type())
                .isEqualTo(ActionType.AUTHORISATION);
        assertThat(ActionVocabulary.classify(
                "ofac issued notice regarding the non-renewal of general lice").type())
                .isEqualTo(ActionType.AUTHORISATION);
    }

    @Test
    void v4AssetsFrozenIsAnAdditionNotConfusedWithALicence() {
        assertThat(ActionVocabulary.classify("assets frozen for a further").type())
                .isEqualTo(ActionType.ADDITION);
    }

    @Test
    void monthTokensAreNeverClassifiedAsVerbs() {
        assertThat(ActionVocabulary.isMonth("nov")).isTrue();
        assertThat(ActionVocabulary.isMonth("removed")).isFalse();
    }

    @Test
    void hasAMeasuredRuleCount() {
        assertThat(ActionVocabulary.ruleCount()).isGreaterThan(50);
    }
}
