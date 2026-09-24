import type { QueryClient } from "@tanstack/react-query";
import {
  createRootRouteWithContext,
  createRoute,
  createRouter,
  redirect,
  type RouterHistory,
} from "@tanstack/react-router";
import { meQueryOptions, type UserProfile } from "./api/auth";
import { AppShell } from "./components/AppShell";
import { LandingPage } from "./routes/LandingPage";
import { LoginPage } from "./routes/LoginPage";

/** Available to every route's `beforeLoad`/`loader` (e.g. for session guards). */
export type RouterContext = { queryClient: QueryClient };

/**
 * The signed-in user's profile, or `null` when there is no session. Asks the server (`GET /me`)
 * unless the session is already cached. Any failure, including a network error or 5xx, counts as
 * "no session", so a guard never crashes the app.
 */
async function currentSession({
  queryClient,
}: RouterContext): Promise<UserProfile | null> {
  try {
    return await queryClient.ensureQueryData(meQueryOptions);
  } catch {
    return null;
  }
}

const rootRoute = createRootRouteWithContext<RouterContext>()({
  component: AppShell,
});

const landingRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/",
  beforeLoad: async ({ context }) => {
    if (!(await currentSession(context))) throw redirect({ to: "/login" });
  },
  component: LandingPage,
});

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/login",
  beforeLoad: async ({ context }) => {
    if (await currentSession(context)) throw redirect({ to: "/" });
  },
  component: LoginPage,
});

const routeTree = rootRoute.addChildren([landingRoute, loginRoute]);

export function createAppRouter({
  queryClient,
  history,
}: {
  queryClient: QueryClient;
  history?: RouterHistory;
}) {
  return createRouter({ routeTree, context: { queryClient }, history });
}

export type AppRouter = ReturnType<typeof createAppRouter>;

declare module "@tanstack/react-router" {
  interface Register {
    router: AppRouter;
  }
}
