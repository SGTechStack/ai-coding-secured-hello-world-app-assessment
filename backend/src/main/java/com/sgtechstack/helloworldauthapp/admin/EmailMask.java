package com.sgtechstack.helloworldauthapp.admin;

/**
 * Reduces an email address to a form that supports account administration
 * without handing over the address itself.
 *
 * <h2>Why mask rather than drop</h2>
 *
 * Dropping the field entirely would be the cleanest privacy answer, and it was
 * the first option considered. It was rejected because the admin screen has one
 * genuine need for it: distinguishing two accounts whose usernames are
 * confusingly similar before disabling the wrong one. Removing the only
 * disambiguating field from a screen whose actions are irreversible trades a
 * privacy problem for a safety one.
 *
 * <p>So the listing carries a masked form, and the full address is available
 * only through a separate, purpose-stated, audited lookup. That matches what the
 * PRD actually says email is for — password reset — by making bulk access to it
 * something nobody does by accident, rather than the default payload of the
 * screen every admin opens.
 *
 * <h2>The residual, stated plainly</h2>
 *
 * The domain is preserved. On a large public mail host that reveals almost
 * nothing; on a small or single-tenant domain it can narrow an account to one
 * organisation or one person. Masking the domain too was considered and dropped
 * because it would leave the field with no disambiguating value at all, which is
 * equivalent to removing it. The trade is recorded here and in
 * {@code docs/adr/0005-personal-data-lawful-basis-and-retention.md} rather than
 * left for a reader to discover.
 *
 * <p>The mask is a fixed four asterisks, not one per hidden character. A
 * variable-length mask would leak the local part's length, which for a short
 * address meaningfully shrinks the guessing space.
 */
final class EmailMask {

    private static final String HIDDEN = "****";
    private static final String UNKNOWN = "<none>";

    private EmailMask() {
    }

    /**
     * {@code "samuel.wong@example.com"} becomes {@code "s****@example.com"}.
     *
     * <p>A value with no {@code @}, or an empty local part, is masked entirely.
     * Registration validates the format so neither should reach here, but a
     * masker that falls back to returning the input unchanged would turn one
     * unexpected row into a disclosure.
     */
    static String of(String email) {
        if (email == null || email.isBlank()) {
            return UNKNOWN;
        }

        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return HIDDEN;
        }

        return email.charAt(0) + HIDDEN + email.substring(at);
    }
}
