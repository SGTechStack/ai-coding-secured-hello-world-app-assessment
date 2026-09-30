# Passwords are hashed with BCrypt, not Argon2id

The standard prefers Argon2id or scrypt and accepts BCrypt only "for existing systems". The PRD requires BCrypt (`BCryptPasswordEncoder`) outright. This is a genuine conflict under ADR-0001, and we follow the PRD: passwords are hashed with BCrypt at cost 12. The hash is stored through a `DelegatingPasswordEncoder`, so each `password_hash` carries its algorithm prefix (`{bcrypt}`). Moving to Argon2id later means adding that encoder and making it the default. Existing BCrypt hashes keep verifying and can be re-hashed on the next successful login, with no bulk migration. BCrypt reads only the first 72 bytes of a password, which is why the password policy caps passwords at 72 bytes of UTF-8.

## Considered Options

- Argon2id now, as the standard prefers: rejected because it contradicts the PRD's explicit requirement. It also needs the Bouncy Castle dependency.
