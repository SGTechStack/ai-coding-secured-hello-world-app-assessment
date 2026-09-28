# Secured Hello World: backend

## Running locally

Every secret and origin is required and has no default in any profile (ADR-062). Startup refuses before the port opens
if one is missing or malformed. Supply them as environment variables:

| Variable | Property | Value |
|---|---|---|
| `APP_MFA_TOTP_ENCRYPTION_KEY` | `app.mfa.totp.encryption.key` | 32 random bytes, padded Base64 |
| `APP_MFA_TOTP_ENCRYPTION_KEYVERSION` | `app.mfa.totp.encryption.key-version` | `0`–`255`, e.g. `1` |
| `APP_SECURITY_HMAC_TOMBSTONE_KEY` | `app.security.hmac.tombstone.key` | 32 random bytes, padded Base64 |
| `APP_SECURITY_HMAC_TOMBSTONE_VERSION` | `app.security.hmac.tombstone.version` | `0` or more, e.g. `1` |
| `APP_SECURITY_HMAC_LOG_KEY` | `app.security.hmac.log.key` | 32 random bytes, padded Base64 |
| `APP_ADMIN_USERNAME` | `app.admin.username` | the seed administrator's username |
| `APP_ADMIN_PASSWORD` | `app.admin.password` | the seed administrator's initial password |
| `APP_ORIGINS_SPA` | `app.origins.spa` | e.g. `http://localhost:5173` |
| `APP_ORIGINS_API` | `app.origins.api` | e.g. `http://localhost:8080` |

Generate each key separately. The three keys must be different: startup refuses a reused key. Use a CSPRNG
(R-CFG-008):

```sh
openssl rand -base64 32
```

```powershell
$b = [byte[]]::new(32); [System.Security.Cryptography.RandomNumberGenerator]::Fill($b); [Convert]::ToBase64String($b)
```

Never use PowerShell's `Get-Random`. Never commit a key, or put one in a committed properties or YAML file.

Then start the app under the `dev` profile:

```sh
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

At startup the log shows one `Key loaded` line per key, with its property, version and an 8-hex-character
fingerprint. The key itself is never logged. Compare fingerprints to confirm which key is live.

The tests need none of these variables. The test harness supplies test-only canary values (`TestSecrets`).
