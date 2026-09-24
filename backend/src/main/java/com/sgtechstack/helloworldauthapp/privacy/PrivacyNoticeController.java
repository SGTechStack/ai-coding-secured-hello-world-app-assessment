package com.sgtechstack.helloworldauthapp.privacy;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the privacy notice.
 *
 * <p>Unauthenticated on purpose, and whitelisted alongside the registration
 * endpoint it exists to precede. A notice that requires an account to read is
 * not a notice at the point of collection: the person deciding whether to hand
 * over an email address has not created an account yet, so gating it behind one
 * would mean the only people who could read it are the ones who no longer need
 * to.
 *
 * <p>The notice contains no personal data — it describes the processing, it does
 * not report on anybody — so there is nothing here to protect.
 */
@RestController
@Configuration
@EnableConfigurationProperties(PrivacyProperties.class)
public class PrivacyNoticeController {

    private final PrivacyProperties privacyProperties;

    public PrivacyNoticeController(PrivacyProperties privacyProperties) {
        this.privacyProperties = privacyProperties;
    }

    @GetMapping("/api/privacy-notice")
    public PrivacyProperties privacyNotice() {
        return privacyProperties;
    }
}
