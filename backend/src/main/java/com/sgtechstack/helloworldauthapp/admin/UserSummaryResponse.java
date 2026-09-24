package com.sgtechstack.helloworldauthapp.admin;

import com.sgtechstack.helloworldauthapp.user.Role;
import com.sgtechstack.helloworldauthapp.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * What an admin is allowed to see about another account.
 *
 * <p>Excludes the password hash, which is the obvious one, and no longer
 * carries the full email address, which was the less obvious one. A single
 * {@code GET /api/admin/users} used to return every registered address in one
 * response — the entire personal-data holding of the system, to any admin, as
 * the default payload of the screen they open to change somebody's role. The
 * PRD says the address exists so a password reset can be delivered; nothing in
 * user administration needs it.
 *
 * <p>{@code maskedEmail} keeps the field's one legitimate use (telling two
 * similarly-named accounts apart before acting on the wrong one) without the
 * disclosure. The real address is reachable through
 * {@code GET /api/admin/users/{id}/email}, one account at a time, with a stated
 * purpose and an audit record.
 *
 * @param maskedEmail see {@link EmailMask} for the form and its residual risk
 */
public record UserSummaryResponse(
        UUID id,
        String username,
        String maskedEmail,
        Role role,
        boolean enabled,
        Instant createdAt
) {
    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getUsername(),
                EmailMask.of(user.getEmail()),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
