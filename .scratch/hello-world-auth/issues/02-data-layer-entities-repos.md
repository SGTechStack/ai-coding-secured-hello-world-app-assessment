# 02: Data layer — users and password_reset_tokens

**What to build:** The persistent data model every story sits on. Two JPA entities and their repositories: `users` (id, unique username, unique email, BCrypt password hash, role enum USER/ADMIN, enabled flag, failed_login_attempts, nullable locked_until, created_at) and `password_reset_tokens` (id, FK to users, hashed token, expires_at, nullable used_at). A `BCryptPasswordEncoder` bean is available for hashing. No endpoints yet — this is the schema and access layer the feature slices build on.

**Blocked by:** 01.

**Status:** done

- [x] `users` entity matches the data model, with unique constraints on username and email
- [x] `password_reset_tokens` entity matches the data model, storing only the token hash (never plaintext)
- [x] Spring Data repositories for both, with the finder methods the later slices need (by username, by email, by token hash)
- [x] `BCryptPasswordEncoder` bean registered
- [x] Schema created on startup for the dev profile; portable to Postgres/MySQL
- [x] Repository slice tests confirm persistence and the unique constraints
