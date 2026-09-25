import type { QueryClient } from "@tanstack/react-query";
import { meQueryOptions } from "./auth";
import { ApiError } from "./client";

/** Whether `error` is a `401`: the server doesn't (or no longer) know this session. */
function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.kind === "unauthorized";
}

/**
 * The app's query retry policy: a `401` is final, since retrying can't bring a session back and
 * would only delay the move to the login page; any other failure is retried up to 3 times.
 */
export function retryUnlessUnauthorized(
  failureCount: number,
  error: unknown,
): boolean {
  return !isUnauthorized(error) && failureCount < 3;
}

/**
 * Sends the user to the login page when the server has ended their session (e.g. an admin
 * disabled them, or they reset their password elsewhere): any query or mutation that fails with
 * `401` while a session profile is cached forgets every cached query, the old session's data
 * included, then calls `leave`. Without a cached profile a `401` is expected (a visitor's `/me`,
 * or a wrong password at login) and is left to the page. Returns a function that stops watching.
 */
export function watchForEndedSession(
  queryClient: QueryClient,
  leave: () => void,
): () => void {
  function handle(error: unknown) {
    if (!isUnauthorized(error)) return;
    if (queryClient.getQueryData(meQueryOptions.queryKey) === undefined) return;
    // Resetting the profile first makes its mounted readers (the header) drop it; clearing then
    // forgets everything else. Without a cached profile, the login route's guard lets them in.
    void queryClient.resetQueries({ queryKey: meQueryOptions.queryKey });
    queryClient.clear();
    leave();
  }
  const stopQueries = queryClient.getQueryCache().subscribe((event) => {
    if (event.type === "updated" && event.action.type === "error")
      handle(event.action.error);
  });
  const stopMutations = queryClient.getMutationCache().subscribe((event) => {
    if (event.type === "updated" && event.action.type === "error")
      handle(event.action.error);
  });
  return () => {
    stopQueries();
    stopMutations();
  };
}
