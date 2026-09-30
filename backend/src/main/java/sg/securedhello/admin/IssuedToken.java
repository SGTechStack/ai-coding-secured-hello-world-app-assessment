package sg.securedhello.admin;

import java.util.UUID;

/**
 * A single-use token returned to an administrator once (ADR-006): an invite's activation token or an admin-issued reset
 * token, sent with {@code Cache-Control: no-store} and never logged (R-FE-007). The table keeps only its hash.
 *
 * @param userId the account the token is for
 * @param token  the plaintext token, 43 characters of Base64url
 */
public record IssuedToken(UUID userId, String token) {

    /** Never the token: a record's generated {@code toString} would put it into any log line that printed one. */
    @Override
    public String toString() {
        return "IssuedToken[userId=" + userId + ", token=<redacted>]";
    }
}
