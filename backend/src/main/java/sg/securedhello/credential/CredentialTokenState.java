package sg.securedhello.credential;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * What the store holds for a token that did not redeem, read only to name the failure on its audit row (row 20).
 *
 * @param usedAt    when it was used, or {@code null} if never
 * @param expiresAt when it expires, or expired
 */
public record CredentialTokenState(@Nullable Instant usedAt, Instant expiresAt) {
}
