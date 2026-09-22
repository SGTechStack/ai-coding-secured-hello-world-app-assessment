> **All directories must be considered.** For each business feature, do not skip any listed directory — each one contains standards or context required for a correct implementation.

> **Build order matters.** For each business feature, all directories listed must be visited in order — each layer depends on the one above it, so build them top-down.

### SSO Login
SSO login, OAuth2, OIDC, identity provider, PKCE, authorization code flow, auto-provisioning, MCC provider hint, `private_key_jwt`
**Build order (read in sequence):**
1. [User_SSO](User_SSO/)

### Standalone Login
Standalone login, username/password, password reset, account lockout, login rate limiting, CSRF bootstrap, user administration, account hygiene, session login, privileged admin
**Build order (read in sequence):**
1. [User_Standalone](User_Standalone/)
