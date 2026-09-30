/**
 * What the Session cache holds, whether seeded by login or restored from the profile: the User's id and the role the
 * Session was granted. The role only decides what the client shows; the server enforces every rule.
 */
export type SessionProfile = { id: string; role: string };
