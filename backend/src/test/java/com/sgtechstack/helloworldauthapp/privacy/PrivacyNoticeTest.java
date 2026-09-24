package com.sgtechstack.helloworldauthapp.privacy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registration collected and indefinitely stored an email address with no notice,
 * no stated purpose, no retention period and no recorded lawful basis.
 *
 * <h2>Why the notice is an endpoint rather than frontend copy</h2>
 *
 * A paragraph in the registration component would have satisfied the letter of the
 * finding and been the weaker fix: copy in a component cannot be asserted on,
 * cannot be reused by a second client, and drifts out of step with what the code
 * does. Holding it in configuration and serving it makes it one source of truth,
 * deployment-configurable where it needs to be (basis and retention differ by
 * jurisdiction), and testable — which is what this class is for.
 *
 * <p>The assertions are about the notice having substance, not about its exact
 * wording. Pinning the prose would make every copy edit a failing build; pinning
 * that each field is populated catches the failure that matters, which is a field
 * quietly emptying out.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PrivacyNoticeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrivacyProperties privacyProperties;

    @Test
    void theNoticeIsReadableWithoutAnAccount() throws Exception {
        // A notice that requires an account to read is not a notice at the point of
        // collection: the person deciding whether to hand over an email address has
        // not registered yet, so gating it would mean the only people who could read
        // it are the ones who no longer need to.
        mockMvc.perform(get("/api/privacy-notice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lawfulBasis").isNotEmpty())
                .andExpect(jsonPath("$.contact").isNotEmpty());
    }

    @Test
    void theNoticeNamesEveryItemOfPersonalDataCollected() {
        // One entry per item, so "we collect some data" cannot stand in for a list.
        assertThat(privacyProperties.dataCollected())
                .isNotEmpty()
                .anySatisfy(entry -> assertThat(entry).containsIgnoringCase("email"))
                .anySatisfy(entry -> assertThat(entry).containsIgnoringCase("username"))
                .anySatisfy(entry -> assertThat(entry).containsIgnoringCase("password"));
    }

    @Test
    void everyItemCollectedIsStatedWithAPurpose() {
        // The finding was as much about purpose as about disclosure. A list of fields
        // with no reason attached is an inventory, not a notice.
        assertThat(privacyProperties.dataCollected())
                .allSatisfy(entry -> assertThat(entry)
                        .as("each entry must say what the item is for: %s", entry)
                        .contains("—"));
    }

    @Test
    void theNoticeStatesARetentionPeriodForEachCategory() {
        assertThat(privacyProperties.retention())
                .isNotEmpty()
                .anySatisfy(entry -> assertThat(entry).containsIgnoringCase("account"))
                .anySatisfy(entry -> assertThat(entry).containsIgnoringCase("reset"))
                .anySatisfy(entry -> assertThat(entry).containsIgnoringCase("log"));
    }

    @Test
    void theStatedResetTokenRetentionMatchesTheConfiguredGrace() {
        // The notice claims 7 days and app.retention.reset-token-grace is P7D. If the
        // two drifted apart, the notice would be a false statement to a data
        // subject — and the notice is the half that people read.
        assertThat(privacyProperties.retention())
                .anySatisfy(entry -> assertThat(entry).contains("7 days"));
    }

    @Test
    void theNoticeDescribesTheRightsThisApplicationActuallyImplements() {
        // Naming a right the software cannot deliver would be worse than naming none:
        // it would be a promise with no endpoint behind it. Both of these resolve to
        // real routes — GET /api/account/export and DELETE /api/account.
        assertThat(privacyProperties.rights())
                .anySatisfy(entry -> assertThat(entry).containsIgnoringCase("access"))
                .anySatisfy(entry -> assertThat(entry).containsIgnoringCase("erasure"));
    }

    @Test
    void theNoticeCarriesADateSoAUserCanTellWhetherItChanged() {
        assertThat(privacyProperties.lastUpdated()).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    void theNoticeItselfContainsNoPersonalData() throws Exception {
        String body = mockMvc.perform(get("/api/privacy-notice"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // It describes the processing; it does not report on anybody. That is what
        // makes serving it unauthenticated safe, and worth pinning because a future
        // "personalised notice" would change that quietly.
        assertThat(body).doesNotContain("@example.com");
    }
}
