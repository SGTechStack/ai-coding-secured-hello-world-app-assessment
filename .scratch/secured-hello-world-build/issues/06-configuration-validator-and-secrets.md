# 06: Configuration validator and secrets

**What to build:** Every application property sits under `app.*` (R-CFG-002). Each secret binds through `@Validated @ConfigurationProperties`, never `@Value`, and has no default in any profile (ADR-062). The secrets are:
- the TOTP key and its version;
- the tombstone HMAC key and its version;
- the log HMAC key;
- the admin seed username and password.

Each key is distinct material (ADR-052). At context refresh, before the port opens, startup refuses:
- a missing or malformed secret;
- an in-memory or unset datasource URL;
- any prohibited configuration: `spring.mvc.servlet.path` (REJ-024), `framework` forward-headers (REJ-015), a cookie `Max-Age` or remember-me (REJ-008), and the email-link logger outside `dev` (ADR-057);
- keys that aren't distinct.

`setRequiredProperties` covers every presence-only required property (T-CFG-038). Each key's fingerprint, never the key itself, is logged at startup.

**Blocked by:** 01

**Status:** done

- [x] Each missing or malformed secret stops startup before the port opens, with a message naming the property but not its value.
- [x] Each prohibited setting stops startup.
- [x] An in-memory or unset datasource URL stops startup.
- [x] Reusing one key for two purposes stops startup.
- [x] A valid configuration logs one fingerprint per key.
- [x] The T-CFG override tests go through `SPRING_APPLICATION_JSON` and assert its arrival first (T-CFG-010).
