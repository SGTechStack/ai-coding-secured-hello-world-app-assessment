# 07: Admin user management: enable/disable, role change, delete

**What to build:** Admin can suspend, promote/demote, or remove other users' accounts. Enable/disable toggles the `enabled` flag (a disabled user can no longer log in). Role change moves an account between `USER` and `ADMIN`. Delete removes the account. All three mutation endpoints reject the request when an admin targets their own account — an admin cannot disable, demote, or delete themselves.

**Blocked by:** 06 (extends the admin bootstrap + user listing endpoints)

**Status:** done

- [x] Admin targets another user's account via the status-toggle endpoint → `enabled` flag updated; a disabled user can no longer log in.
- [x] Admin targets their own account via the status-toggle endpoint → rejected.
- [x] Admin targets another user's account via the role-change endpoint with a valid role → role updated.
- [x] Admin targets their own account via the role-change endpoint → rejected.
- [x] Admin targets another user's account via the delete endpoint → account removed.
- [x] Admin targets their own account via the delete endpoint → rejected.
- [x] Structured audit log lines for role change / enable / disable / delete, capturing actor + target (never logging passwords).
- [x] React app admin view supports triggering these three actions per user row.

## Implementation notes

**Backend**

- `AdminUserManagementService` (new, in `admin` package): `setEnabled`, `changeRole`, `deleteUser`. Each takes the acting admin's id explicitly (extracted from `@AuthenticationPrincipal` in the controller, not re-derived from a security context lookup inside the service) and calls a shared `requireNotSelf` guard that throws `SelfActionNotAllowedException` (400) before touching the target. This is a business rule, not an authorization rule — the `ROLE_ADMIN` check itself stays in `SecurityConfig`, same as ticket 06.
- `AdminUserController` gained `PATCH /api/admin/users/{id}/enabled`, `PATCH /api/admin/users/{id}/role`, `DELETE /api/admin/users/{id}`, all returning the updated `UserSummaryResponse` (or 204 for delete).
- Disabling a user blocks their login for free — `UserPrincipal.isEnabled()` was already wired to `User.enabled` back in ticket 03, so no new login-path code was needed; Spring Security's `DisabledException` handles the rejection the same way `LockedException` already does for account lockout.
- **Delete cascade**: rather than adding a JPA `@OneToMany` cascade from `User` to `PasswordResetToken` (which would make the `user` package depend on `passwordreset`, inverting the current, cleaner dependency direction), `deleteUser` explicitly clears the target's reset tokens via a new `PasswordResetTokenRepository.deleteAllByUser` before deleting the user. This is the same foreign-key constraint that caused the test-isolation bug fixed in ticket 06, now handled correctly in the actual delete path rather than only worked around in tests.
- Structured audit log lines (`AdminUserManagementService`, one per action) capture the actor's id, the target's username, and the action type (`SET_ENABLED` / `CHANGE_ROLE` / `DELETE`) — never a password.

**Frontend**

- `AdminUserList.tsx` gained per-row controls: an Enable/Disable toggle button, a role `<select>`, and a Delete button. All three are disabled on the acting admin's own row (`user.username === currentUsername`) as a UI convenience — the real enforcement is server-side, verified independently in tests and live. Any action re-fetches the list afterward so the table reflects the change immediately.
- `ProtectedGreeting` now passes the logged-in admin's own username down to `AdminUserList`, needed for that self-row detection.
- `api/client.ts` gained `setUserEnabled`, `setUserRole`, `deleteUser`.

**Tests**

- Extended `AdminUserControllerTest` (not a new file) with: disable blocks a subsequent login attempt for that account; self-disable rejected with the account left untouched; role change updates the target's role; self-role-change rejected; delete removes the account; self-delete rejected with the account left untouched; a non-admin gets 403 on every mutation endpoint.
- `mvn clean verify`: 34/34 tests pass, BUILD SUCCESS.
- Verified live end to end through the real UI: logged in as the seeded admin, saw the admin's own row with all three controls correctly disabled, registered a second account, disabled it, promoted it to `ADMIN`, then deleted it — each action's audit log line appeared on the backend (actor id, target username, action, no password), and the table refreshed correctly after each step.
