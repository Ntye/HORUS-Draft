package horus.domain.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.DecisionBand;
import horus.domain.shared.ListVersionId;
import horus.domain.shared.ScreeningId;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScreeningRequestTest {

    @Test
    void acceptsAValidRequestAndSortsListVersionIds() {
        ListVersionId first = new ListVersionId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        ListVersionId second = new ListVersionId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        Set<ListVersionId> insertedOutOfOrder = new LinkedHashSet<>();
        insertedOutOfOrder.add(first);
        insertedOutOfOrder.add(second);

        ScreeningRequest request = new ScreeningRequest(
                new ScreeningId(UUID.randomUUID()),
                "payments-gateway",
                Optional.of("PAY-2026-0009812"),
                "payment-beneficiary",
                "svc-payments",
                Instant.parse("2026-09-21T09:14:02Z"),
                insertedOutOfOrder,
                UUID.randomUUID(),
                "pipeline-v1",
                DecisionBand.NO_MATCH,
                Optional.empty(),
                "4d2a2b7c-9f1e-4a58-b0b1-6c9f2b7a1c33");

        assertThat(request.listVersionIds()).containsExactly(second, first);
    }

    @Test
    void listVersionIdsIsUnmodifiable() {
        ScreeningRequest request = validRequest();

        assertThatThrownBy(() -> request.listVersionIds().add(new ListVersionId(UUID.randomUUID())))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsEmptyListVersionIds() {
        assertThatThrownBy(() -> new ScreeningRequest(
                new ScreeningId(UUID.randomUUID()),
                "payments-gateway",
                Optional.empty(),
                "payment-beneficiary",
                "svc-payments",
                Instant.parse("2026-09-21T09:14:02Z"),
                Set.of(),
                UUID.randomUUID(),
                "pipeline-v1",
                DecisionBand.NO_MATCH,
                Optional.empty(),
                "idem-key"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankIdempotencyKey() {
        assertThatThrownBy(() -> new ScreeningRequest(
                new ScreeningId(UUID.randomUUID()),
                "payments-gateway",
                Optional.empty(),
                "payment-beneficiary",
                "svc-payments",
                Instant.parse("2026-09-21T09:14:02Z"),
                Set.of(new ListVersionId(UUID.randomUUID())),
                UUID.randomUUID(),
                "pipeline-v1",
                DecisionBand.NO_MATCH,
                Optional.empty(),
                " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ScreeningRequest validRequest() {
        return new ScreeningRequest(
                new ScreeningId(UUID.randomUUID()),
                "payments-gateway",
                Optional.empty(),
                "payment-beneficiary",
                "svc-payments",
                Instant.parse("2026-09-21T09:14:02Z"),
                Set.of(new ListVersionId(UUID.randomUUID())),
                UUID.randomUUID(),
                "pipeline-v1",
                DecisionBand.NO_MATCH,
                Optional.empty(),
                "idem-key");
    }
}
