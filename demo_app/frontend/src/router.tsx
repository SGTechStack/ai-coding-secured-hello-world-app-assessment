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
import { AdminUsersPage } from "./routes/AdminUsersPage";
import { LandingPage } from "./routes/LandingPage";
import { LoginPage } from "./routes/LoginPage";
import { RegisterPage } from "./routes/RegisterPage";
import { parseLoginSearch } from "./routes/loginNotice";

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

/** Only for visitors: an existing session goes to the landing page instead. */
async function requireNoSession({ context }: { context: RouterContext }) {
  if (await currentSession(context)) throw redirect({ to: "/" });
}

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/login",
  // A one-shot notice (e.g. after registering), chosen from a fixed set; see loginNotice.ts.
  validateSearch: parseLoginSearch,
  beforeLoad: requireNoSession,
  component: LoginPage,
});

const registerRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/register",
  beforeLoad: requireNoSession,
  component: RegisterPage,
});

/**
 * Only for admins: no session goes to `/login`, and a signed-in non-admin to the landing page, so
 * neither ever sees a broken admin screen. The API enforces the role on its own.
 */
const adminUsersRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: "/admin/users",
  beforeLoad: async ({ context }) => {
    const session = await currentSession(context);
    if (!session) throw redirect({ to: "/login" });
    if (session.role !== "ADMIN") throw redirect({ to: "/" });
  },
  component: AdminUsersPage,
});

const routeTree = rootRoute.addChildren([
  landingRoute,
  loginRoute,
  registerRoute,
  adminUsersRoute,
]);

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
