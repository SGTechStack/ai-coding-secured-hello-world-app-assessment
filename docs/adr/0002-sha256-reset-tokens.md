# Reset tokens are SHA-256; passwords stay BCrypt

Confirming a reset has to find one row from the token the caller presents. Passwords stay on `BCryptPasswordEncoder`.

## Why the two hashes differ

BCrypt embeds a random salt, so the same password does not produce the same string twice. That is what we want for a password: you check a guess, you do not look the hash up. A reset token has to be found with one query. A salted hash would mean loading every unused token and running `matches` on each.

The token is 32 bytes from `SecureRandom`, encoded as URL-safe Base64 with no padding. It is not a short human password, so a slow hash does not buy brute-force resistance that the entropy does not already provide.

## Decision

`TokenHasher` stores the SHA-256 hex digest in `password_reset_tokens.token_hash`. The raw token is only placed in the reset link that `LoggingEmailService` logs. A new request deletes that account's older tokens. Confirm rejects a missing, expired, or already used digest, then marks `used_at`.

Passwords are still BCrypt, including the seeded admin password.
