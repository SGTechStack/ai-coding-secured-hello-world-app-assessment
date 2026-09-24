package com.sgtechstack.helloworldauthapp.privacy;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds the {@code app.privacy} block: the privacy notice this application
 * shows at the point it collects personal data.
 *
 * <h2>Why the notice is configuration rather than frontend copy</h2>
 *
 * The finding was that registration collected and indefinitely stored an email
 * address with no notice, no stated purpose, no retention period and no recorded
 * lawful basis. The obvious fix is a paragraph of text in the registration form,
 * and that alone would have been the wrong fix: copy in a component is copy
 * nobody can assert on, reuse, or keep in step with what the code actually does.
 *
 * <p>Holding it here instead gives three properties worth having. The notice is
 * served from the API, so the SPA and any future client show the same text
 * rather than each inventing their own. It is deployment-configurable, which
 * matters because the lawful basis and the retention periods are the parts most
 * likely to differ per jurisdiction and per operator. And it is testable — there
 * is an assertion that the notice names a purpose, a basis and a retention
 * period, so the fields cannot quietly empty out.
 *
 * <p>The values shipped in {@code application.yml} are drafted against general
 * PDPA/GDPR-style expectations, because the PRD declares no specific regime.
 * They are a starting point for a compliance review, not the output of one. See
 * {@code docs/adr/0005-personal-data-lawful-basis-and-retention.md}, which
 * records that distinction so nobody mistakes a default for a decision.
 *
 * @param controller        who is accountable for the data
 * @param contact           where a data subject sends a request or complaint
 * @param lawfulBasis       the basis relied on for processing
 * @param dataCollected     each item collected, with what it is for — one entry
 *                          per item, so "we collect some data" cannot stand in
 *                          for an actual list
 * @param retention         how long each item is kept
 * @param rights            what a data subject can do, including the
 *                          self-service paths this application implements
 * @param lastUpdated       ISO date the notice text last changed, so a user can
 *                          tell whether they have seen the current version
 */
@ConfigurationProperties(prefix = "app.privacy")
public record PrivacyProperties(
        String controller,
        String contact,
        String lawfulBasis,
        List<String> dataCollected,
        List<String> retention,
        List<String> rights,
        String lastUpdated
) {
}
