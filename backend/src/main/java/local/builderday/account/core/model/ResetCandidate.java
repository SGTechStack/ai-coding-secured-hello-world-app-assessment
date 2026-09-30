package local.builderday.account.core.model;

import java.util.UUID;

/**
 * The account an email belongs to, as Password reset sees it.
 *
 * @param username normalized username, which is also the Session principal name
 * @param email normalized email the reset link is sent to
 * @param eligible true only for an enabled, non-deleted account with an email that holds exactly the self-service
 *     (registration) role, so never an Admin (ADR 0004)
 */
public record ResetCandidate(UUID id, String username, String email, boolean eligible) {}
