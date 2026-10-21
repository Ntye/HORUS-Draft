package horus.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.application.ingestion.testsupport.FakeAuditEventPort;
import horus.application.ingestion.testsupport.FakeIdGenerator;
import horus.application.ingestion.testsupport.FakeListVersionRepository;
import horus.domain.listversion.ApprovalMode;
import horus.domain.listversion.ListVersion;
import horus.domain.listversion.LoadApproval;
import horus.domain.listversion.LoadStatus;
import horus.domain.listversion.ReviewState;
import horus.domain.shared.ListVersionId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Without an acknowledgement path, --approval=NOT_REQUIRED would simply be a way to switch the
// capability gate off: the load promotes, the waiver sits PENDING forever, and nobody is ever
// required to look. The point of moving the decision after promotion is that it still gets made.
class AcknowledgeLoadReviewTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T09:00:00Z"), ZoneOffset.UTC);

    private static ListVersion version(ListVersionId id, LoadApproval approval) {
        return new ListVersion(id, "worldcheck", "worldcheck-xml-v1", Optional.empty(),
                "wc.xml", "sha256:" + id.value(), Optional.empty(), Instant.parse("2026-10-01T02:00:00Z"),
                5_990_394L, LoadStatus.ACTIVE, "svc-cron", Optional.empty(), "1.0.0", Optional.empty(),
                approval);
    }

    private record Fixture(FakeListVersionRepository repository, FakeAuditEventPort audit,
            AcknowledgeLoadReview interactor) {
    }

    private static Fixture fixture() {
        FakeListVersionRepository repository = new FakeListVersionRepository();
        FakeAuditEventPort audit = new FakeAuditEventPort();
        return new Fixture(repository, audit, new AcknowledgeLoadReview(
                repository, new RecordAuditEvent(audit, new FakeIdGenerator(), CLOCK)));
    }

    @Test
    void acknowledgingClearsTheReviewAndRecordsTheReason() {
        Fixture f = fixture();
        ListVersionId id = new ListVersionId(UUID.randomUUID());
        f.repository().save(version(id, LoadApproval.unattended().owingReview()));

        f.interactor().execute(new AcknowledgeLoadReview.Command(
                id, "coverage drift matches the vendor's published change note", "j.reviewer"));

        assertThat(f.repository().findById(id).orElseThrow().approval().reviewState())
                .isEqualTo(ReviewState.ACKNOWLEDGED);
        assertThat(f.audit().recorded())
                .anySatisfy(e -> assertThat(e.eventType()).isEqualTo("LOAD_REVIEW_ACKNOWLEDGED"));
    }

    // The load is already serving. Acknowledging says a human has seen why -- it must not touch
    // the active pointer, in either direction.
    @Test
    void acknowledgingDoesNotChangeWhichVersionIsServing() {
        Fixture f = fixture();
        ListVersionId id = new ListVersionId(UUID.randomUUID());
        f.repository().save(version(id, LoadApproval.unattended().owingReview()));

        f.interactor().execute(new AcknowledgeLoadReview.Command(id, "reviewed", "j.reviewer"));

        assertThat(f.repository().findById(id).orElseThrow().status()).isEqualTo(LoadStatus.ACTIVE);
    }

    @Test
    void aLoadThatOwesNoReviewCannotBeAcknowledged() {
        Fixture f = fixture();
        ListVersionId id = new ListVersionId(UUID.randomUUID());
        f.repository().save(version(id, LoadApproval.required()));

        assertThatThrownBy(() -> f.interactor().execute(
                new AcknowledgeLoadReview.Command(id, "nothing to see", "j.reviewer")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acknowledgingTwiceIsRefusedRatherThanSilentlyIgnored() {
        Fixture f = fixture();
        ListVersionId id = new ListVersionId(UUID.randomUUID());
        f.repository().save(version(id, LoadApproval.unattended().owingReview()));
        f.interactor().execute(new AcknowledgeLoadReview.Command(id, "reviewed", "j.reviewer"));

        assertThatThrownBy(() -> f.interactor().execute(
                new AcknowledgeLoadReview.Command(id, "reviewed again", "j.reviewer")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aReasonIsMandatory() {
        ListVersionId id = new ListVersionId(UUID.randomUUID());

        assertThatThrownBy(() -> new AcknowledgeLoadReview.Command(id, "  ", "j.reviewer"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // LoadApproval refuses the one combination that would be a lie: a load a human approved before
    // it served cannot also be owed a review afterwards.
    @Test
    void aRequiredLoadCannotCarryAReviewState() {
        assertThatThrownBy(() -> new LoadApproval(ApprovalMode.REQUIRED, ReviewState.PENDING))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
