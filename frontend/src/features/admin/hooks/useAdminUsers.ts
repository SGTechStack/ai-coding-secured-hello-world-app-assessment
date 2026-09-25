import { useCallback, useEffect, useState } from "react";

import { isAbortError } from "@/common/api/http";
import type { UserRole } from "@/features/auth/api";
import {
  deleteUser,
  fetchAdminUsers,
  fetchUserEmail,
  setUserEnabled,
  setUserRole,
  unlockUser,
  type AdminUserSummary,
} from "../api";

export type ListState =
  | { kind: "loading" }
  | { kind: "success"; users: AdminUserSummary[] }
  | { kind: "error"; message: string };

/**
 * A pending request for something the operator has to supply before an action can
 * proceed: their password for an irreversible delete, or a purpose for revealing
 * an email address.
 *
 * Modelled as state rather than handled with `window.prompt` because a native
 * prompt cannot mask a password field, is blocked by some browsers, and gives no
 * room to explain why the value is being asked for — and an unexplained password
 * prompt is exactly the habit that makes people type passwords into anything.
 */
export type Challenge =
  | { kind: "confirm-delete"; user: AdminUserSummary; value: string; error: string | null }
  | { kind: "state-purpose"; user: AdminUserSummary; value: string; error: string | null };

/**
 * All state and mutation logic behind the admin user list: loading/refreshing
 * the table, per-row pending flags, the reveal-email/confirm-delete challenge
 * flow, and the session-scoped cache of revealed addresses.
 *
 * Kept as one hook rather than several smaller ones because the pieces are not
 * independently useful — a challenge only makes sense in terms of the row list
 * it acts on, and every mutation ends by re-running the same `load`.
 */
export function useAdminUsers() {
  const [state, setState] = useState<ListState>({ kind: "loading" });
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [pendingUserIds, setPendingUserIds] = useState<ReadonlySet<string>>(new Set());
  const [challenge, setChallenge] = useState<Challenge | null>(null);
  /**
   * Addresses revealed during this session, keyed by user id.
   *
   * Deliberately not persisted and not prefetched. Each entry cost a
   * purpose-stated, audited request, and keeping them only in component state
   * means closing the panel discards them rather than leaving a local cache of
   * every address an admin has ever looked at.
   */
  const [revealedEmails, setRevealedEmails] = useState<ReadonlyMap<string, string>>(new Map());

  /**
   * Fetches the user list. A *refresh* (after a mutation) keeps the existing
   * rows mounted and only flags `isRefreshing`; a first load clears to the
   * loading state. The distinction matters: dropping back to `{ kind: "loading" }`
   * would unmount the whole table subtree, so React would delete every row's
   * DOM node and rebuild it, throwing away scroll position and keyboard focus.
   */
  const load = useCallback(async (isRefresh: boolean, signal?: AbortSignal) => {
    if (isRefresh) {
      setIsRefreshing(true);
    } else {
      setState({ kind: "loading" });
    }

    try {
      setState({ kind: "success", users: await fetchAdminUsers(signal) });
    } catch (error: unknown) {
      if (isAbortError(error)) return;
      setState({
        kind: "error",
        message: error instanceof Error ? error.message : "Could not load users",
      });
    } finally {
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void load(false, controller.signal);
    return () => controller.abort();
  }, [load]);

  /**
   * Runs a single-user mutation, then awaits the refresh before releasing the
   * row. Awaiting matters: returning early would re-enable the controls while
   * the list was still in flight, and two quick actions would leave two
   * refetches racing with no ordering guarantee — the slower response would win
   * and could repaint pre-mutation data.
   */
  async function runAction(userId: string, action: () => Promise<unknown>) {
    setActionError(null);
    // A Set rather than a single id, so overlapping actions on different rows
    // can't clobber each other's pending flag. Always a fresh Set: mutating the
    // previous one in place would leave the reference unchanged, and React would
    // bail out of the re-render because `Object.is(prev, next)` holds.
    setPendingUserIds((prev) => new Set(prev).add(userId));
    try {
      await action();
      await load(true);
    } catch (error: unknown) {
      setActionError(error instanceof Error ? error.message : "Action failed");
    } finally {
      setPendingUserIds((prev) => {
        const next = new Set(prev);
        next.delete(userId);
        return next;
      });
    }
  }

  function toggleEnabled(user: AdminUserSummary) {
    void runAction(user.id, () => setUserEnabled(user.id, !user.enabled));
  }

  function changeRole(user: AdminUserSummary, newRole: UserRole) {
    if (newRole === user.role) return;
    void runAction(user.id, () => setUserRole(user.id, newRole));
  }

  function unlock(user: AdminUserSummary) {
    void runAction(user.id, () => unlockUser(user.id));
  }

  /**
   * Submits whichever challenge is open.
   *
   * Errors are shown inside the challenge rather than in the panel-level banner,
   * so a wrong password leaves the form open with the message attached to it.
   * Clearing the form and reporting the failure elsewhere would make a mistyped
   * password look like a refusal to act.
   */
  async function submitChallenge() {
    if (!challenge) return;

    if (!challenge.value.trim()) {
      setChallenge({ ...challenge, error: "This is required" });
      return;
    }

    const { kind, user, value } = challenge;

    try {
      if (kind === "confirm-delete") {
        await deleteUser(user.id, value);
        setChallenge(null);
        // Clear any revealed address for the deleted account rather than leaving
        // it on screen attached to a row that no longer exists.
        setRevealedEmails((prev) => {
          const next = new Map(prev);
          next.delete(user.id);
          return next;
        });
        await load(true);
      } else {
        const revealed = await fetchUserEmail(user.id, value);
        setRevealedEmails((prev) => new Map(prev).set(user.id, revealed.email));
        setChallenge(null);
      }
    } catch (error: unknown) {
      setChallenge({
        ...challenge,
        error: error instanceof Error ? error.message : "Request failed",
      });
    }
  }

  return {
    state,
    isRefreshing,
    actionError,
    pendingUserIds,
    challenge,
    setChallenge,
    revealedEmails,
    toggleEnabled,
    changeRole,
    unlock,
    submitChallenge,
  };
}
