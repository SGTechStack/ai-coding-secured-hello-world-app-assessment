package com.sgtechstack.helloworldauthapp.admin;

import java.util.UUID;

/**
 * The full email address of one account, returned by the purpose-stated lookup
 * that replaced bulk email disclosure in the user listing.
 *
 * <p>Echoes back the purpose the caller supplied. Not for the caller's benefit
 * — they wrote it — but so that a response captured in a browser's network tab
 * or a support transcript carries the justification alongside the data, rather
 * than the data alone.
 */
public record UserEmailResponse(UUID id, String username, String email, String purpose) {
}
