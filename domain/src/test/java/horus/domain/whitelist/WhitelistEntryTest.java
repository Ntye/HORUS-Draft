package horus.domain.whitelist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.EntityId;
import horus.domain.shared.EntityVersionId;
import horus.domain.shared.WhitelistId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WhitelistEntryTest {

    @Test
    void isActiveForTheApprovedVersion() {
        EntityVersionId approvedVersion = new EntityVersionId(UUID.randomUUID());
        WhitelistEntry entry = new WhitelistEntry(
                new WhitelistId(UUID.randomUUID()),
                WhitelistScope.GLOBAL,
                "sahara trading ltd",
                new EntityId(UUID.randomUUID()),
                approvedVersion,
                "Confirmed false positive against a common trading name.",
                "compliance-analyst-1",
                Instant.parse("2026-06-01T00:00:00Z"),
                Optional.empty());

        assertThat(entry.isActiveFor(approvedVersion)).isTrue();
    }

    @Test
    void goesInactiveWhenTheCurrentVersionDiffers() {
        EntityVersionId approvedVersion = new EntityVersionId(UUID.randomUUID());
        EntityVersionId newerVersion = new EntityVersionId(UUID.randomUUID());
        WhitelistEntry entry = new WhitelistEntry(
                new WhitelistId(UUID.randomUUID()),
                WhitelistScope.PROFILE,
                "sahara trading ltd",
                new EntityId(UUID.randomUUID()),
                approvedVersion,
                "Confirmed false positive against a common trading name.",
                "compliance-analyst-1",
                Instant.parse("2026-06-01T00:00:00Z"),
                Optional.empty());

        // I-8: staleness only affects downstream scoring; the entry itself is untouched.
        assertThat(entry.isActiveFor(newerVersion)).isFalse();
        assertThat(entry.approvedAgainstVersionId()).isEqualTo(approvedVersion);
    }

    @Test
    void rejectsBlankJustification() {
        assertThatThrownBy(() -> new WhitelistEntry(
                new WhitelistId(UUID.randomUUID()),
                WhitelistScope.GLOBAL,
                "sahara trading ltd",
                new EntityId(UUID.randomUUID()),
                new EntityVersionId(UUID.randomUUID()),
                " ",
                "compliance-analyst-1",
                Instant.parse("2026-06-01T00:00:00Z"),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
