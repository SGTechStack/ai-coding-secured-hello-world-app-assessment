package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import sg.securedhello.testsupport.Proves;

/**
 * The ASVS 16.1.1 log inventory is generated from {@link AuditEvent} as a snapshot (R-AUD-027), run by Failsafe in
 * {@code verify}: the committed {@code docs/audit/log-inventory.md} must equal what {@link AuditInventory} renders now.
 *
 * <p>After changing the catalogue, regenerate with {@code -Daudit-inventory.regenerate=true} and commit the result.
 */
class AuditInventoryDriftIT {

    @Test
    @Proves("T-AUD-017")
    void theCommittedInventoryMatchesTheEnum() throws IOException {
        String generated = AuditInventory.markdown();
        if (Boolean.getBoolean("audit-inventory.regenerate")) {
            Files.createDirectories(AuditInventory.MARKDOWN.getParent());
            Files.writeString(AuditInventory.MARKDOWN, generated, StandardCharsets.UTF_8);
        }
        assertThat(AuditInventory.MARKDOWN).as("%s exists; regenerate with -Daudit-inventory.regenerate=true",
                AuditInventory.MARKDOWN).exists();
        assertThat(Files.readString(AuditInventory.MARKDOWN, StandardCharsets.UTF_8).replace("\r\n", "\n"))
                .as("%s is out of date with AuditEvent; regenerate with -Daudit-inventory.regenerate=true",
                        AuditInventory.MARKDOWN)
                .isEqualTo(generated);
    }

    @Test
    @Proves("T-AUD-017")
    void theInventoryListsEveryEventWithItsDestinationsRetentionAndReaders() {
        String inventory = AuditInventory.markdown();

        assertThat(inventory).contains("| Destination | Format | Retention | Who can read |", "audit.ndjson",
                "Standard output", "90 archives");
        for (AuditEvent event : AuditEvent.values()) {
            assertThat(inventory).contains("| " + event.name() + " | `" + event.definition().action() + "`");
        }
    }
}
