---
status: proposed
---

# Keep JPA entities and Spring Security types in `user/domain`

`AppUser`, `AppUserRole` and `AppUserDetails` carry JPA and Spring Security types although `backend/CLAUDE.md` keeps `domain` free of them. Here the entities are the domain model: there is no separate persistence model, and mapping every Account to a twin class would add code without a second implementation to justify it. The template predates the purity rule, and moving the classes now would touch every repository and test for no behavior change.

## Consequences

- This is a deliberate, documented exception (the rule allows one) limited to those three classes; new domain code stays free of JPA and security types.
- Reversing it later means introducing a persistence model in `infrastructure` and a mapper, which is why it is recorded.
