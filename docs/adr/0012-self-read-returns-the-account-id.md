# The self-read endpoint returns the Account's id

The self-read recipe leaves internal UUIDs out of the profile view. `GET /me` nevertheless returns the caller's Account id, along with username, email and role. The SPA needs it:

- The admin endpoints address Accounts by id.
- The admin page must recognise the caller's own row so it can hide the disable, demote and delete actions that the server would reject as `self action not allowed`.

Leaving the id out would force that comparison onto the username, which is display data rather than a stable key. The id is a random UUID that reveals nothing, and `/me` only ever returns the caller's own Account. Access control never depends on the id staying secret: every endpoint authorises the caller on the server.

## Considered Options

- Omit the id, as the recipe does: rejected because the SPA would have to match rows by username.
