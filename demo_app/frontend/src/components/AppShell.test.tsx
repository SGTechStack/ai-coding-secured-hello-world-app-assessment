import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { csrfToken, demoUser } from "../test/handlers";
import { renderApp } from "../test/renderApp";
import { server } from "../test/server";

const unauthorized = () =>
  HttpResponse.json({ message: "Unauthorized" }, { status: 401 });

type LogoutReply = number | "network-error" | Promise<number>;

/**
 * A server with one session: `/me` reports `demoUser` from the start (or from login, when
 * `signedIn` is false) until a logout ends it. Each logout gets the next of `logoutReplies` in turn
 * (a status, a network error, or a status that arrives later); 204 once they run out. Returns the
 * logout requests and counts of CSRF primes and logouts.
 */
function fakeSession({
  signedIn = true,
  logoutReplies = [],
}: { signedIn?: boolean; logoutReplies?: LogoutReply[] } = {}) {
  const logoutRequests: Request[] = [];
  const counts = { primes: 0, logouts: 0 };
  server.use(
    http.get("/api/v1/auth/me", () =>
      signedIn ? HttpResponse.json(demoUser) : unauthorized(),
    ),
    http.get("/api/v1/auth/csrf", () => {
      counts.primes += 1;
      return new HttpResponse(null, {
        status: 204,
        headers: { "Set-Cookie": `XSRF-TOKEN=${csrfToken}; Path=/` },
      });
    }),
    http.post("/api/v1/auth/login", () => {
      signedIn = true;
      return HttpResponse.json(demoUser);
    }),
    http.post("/api/v1/auth/logout", async ({ request }) => {
      logoutRequests.push(request.clone());
      const reply = logoutReplies[counts.logouts] ?? 204;
      counts.logouts += 1;
      if (reply === "network-error") return HttpResponse.error();
      const status = await reply;
      // A 204 ends the session; a 401 says there was none to end.
      if (status === 204 || status === 401) signedIn = false;
      return status === 204
        ? new HttpResponse(null, { status })
        : HttpResponse.json({ message: "Failed" }, { status });
    }),
  );
  return { logoutRequests, counts };
}

async function signedInOnLanding() {
  const app = renderApp("/");
  await screen.findByRole("heading", { name: "Hello, John!" });
  return app;
}

describe("navbar Log out", () => {
  it("is shown to a signed-in user", async () => {
    server.use(http.get("/api/v1/auth/me", () => HttpResponse.json(demoUser)));
    await signedInOnLanding();

    expect(screen.getByRole("button", { name: "Log out" })).toBeEnabled();
    expect(
      screen.getByRole("button", { name: "Toggle theme" }),
    ).toBeInTheDocument();
  });

  it("is not shown to an anonymous visitor on /login", async () => {
    renderApp("/login");

    expect(await screen.findByLabelText("Username")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Log out" }),
    ).not.toBeInTheDocument();
  });

  it("ends the session with one CSRF-protected POST and returns to the login form", async () => {
    const { logoutRequests: logouts } = fakeSession();
    const { user, router } = await signedInOnLanding();

    await user.click(screen.getByRole("button", { name: "Log out" }));

    expect(await screen.findByLabelText("Username")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
    expect(screen.queryByText("Hello, John!")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Log out" }),
    ).not.toBeInTheDocument();
    expect(logouts).toHaveLength(1);
    expect(logouts[0]?.headers.get("X-XSRF-TOKEN")).toBe(csrfToken);
  });

  it("replaces the history entry, so going back does not return to /", async () => {
    fakeSession({ signedIn: false });
    const { user, router } = renderApp("/login");
    await user.type(await screen.findByLabelText("Username"), "johndoe");
    await user.type(screen.getByLabelText("Password"), "Password123!");
    await user.click(screen.getByRole("button", { name: "Log in" }));
    await screen.findByRole("heading", { name: "Hello, John!" });

    await user.click(screen.getByRole("button", { name: "Log out" }));
    await screen.findByLabelText("Username");
    // /login, then / after login; logout replaced / instead of pushing a third entry.
    expect(router.history.length).toBe(2);
    router.history.back();

    await waitFor(() => expect(router.state.location.pathname).toBe("/login"));
    expect(screen.getByLabelText("Username")).toBeInTheDocument();
    expect(screen.queryByText("Hello, John!")).not.toBeInTheDocument();
  });

  it("clears the cached session, so / asks /me again and redirects to /login", async () => {
    fakeSession();
    const { user, router } = await signedInOnLanding();
    await user.click(screen.getByRole("button", { name: "Log out" }));
    await screen.findByLabelText("Username");

    let meRequests = 0;
    server.use(
      http.get("/api/v1/auth/me", () => {
        meRequests += 1;
        return unauthorized();
      }),
    );
    await router.navigate({ to: "/" });

    await waitFor(() => expect(router.state.location.pathname).toBe("/login"));
    // A cached profile would have let `/` through without asking; the `/login` guard then asks too.
    expect(meRequests).toBeGreaterThan(0);
    expect(screen.getByLabelText("Username")).toBeInTheDocument();
    expect(screen.queryByText("Hello, John!")).not.toBeInTheDocument();
  });

  it("lets a different user log in straight after and greets them by name", async () => {
    fakeSession();
    const { user, router } = await signedInOnLanding();
    await user.click(screen.getByRole("button", { name: "Log out" }));

    server.use(
      http.post("/api/v1/auth/login", () =>
        HttpResponse.json({ username: "janedoe", firstName: "Jane" }),
      ),
    );
    await user.type(await screen.findByLabelText("Username"), "janedoe");
    await user.type(screen.getByLabelText("Password"), "Password123!");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    expect(
      await screen.findByRole("heading", { name: "Hello, Jane!" }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/");
    expect(screen.queryByText("Hello, John!")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Log out" })).toBeEnabled();
  });
});

describe("navbar Log out, pending and failure", () => {
  const failure = "Unable to log out. Please try again.";

  /** A logout status the test sends later with `finish(status)`, to observe the pending state. */
  function deferredStatus() {
    let finish!: (status: number) => void;
    const status = new Promise<number>((resolve) => (finish = resolve));
    return { status, finish };
  }

  it("disables the button and reads Logging out... while the request is in flight", async () => {
    const pending = deferredStatus();
    fakeSession({ logoutReplies: [pending.status] });
    const { user, router } = await signedInOnLanding();

    await user.click(screen.getByRole("button", { name: "Log out" }));

    expect(
      await screen.findByRole("button", { name: "Logging out..." }),
    ).toBeDisabled();
    pending.finish(204);
    await waitFor(() => expect(router.state.location.pathname).toBe("/login"));
  });

  it("treats a 401 as logged out and shows no alert", async () => {
    const { counts } = fakeSession({ logoutReplies: [401] });
    const { user, router } = await signedInOnLanding();

    await user.click(screen.getByRole("button", { name: "Log out" }));

    expect(await screen.findByLabelText("Username")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(counts.logouts).toBe(1);
  });

  it("re-primes the CSRF token after a 403 and retries once", async () => {
    const { counts } = fakeSession({ logoutReplies: [403, 204] });
    const { user, router } = await signedInOnLanding();

    await user.click(screen.getByRole("button", { name: "Log out" }));

    expect(await screen.findByLabelText("Username")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/login");
    // One prime before the first POST (no cookie yet), one for the retry.
    expect(counts).toEqual({ primes: 2, logouts: 2 });
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it.each<[string, LogoutReply[]]>([
    ["a network error", ["network-error"]],
    ["a 5xx", [503]],
    ["a 403 twice", [403, 403]],
  ])(
    "stays on / with an alert after %s, and clears it on the next attempt",
    async (_, replies) => {
      const nextAttempt = deferredStatus();
      const { counts } = fakeSession({
        logoutReplies: [...replies, nextAttempt.status],
      });
      const { user, router } = await signedInOnLanding();

      await user.click(screen.getByRole("button", { name: "Log out" }));

      expect(await screen.findByRole("alert")).toHaveTextContent(failure);
      expect(router.state.location.pathname).toBe("/");
      expect(screen.getByText("Hello, John!")).toBeInTheDocument();
      expect(counts.logouts).toBe(replies.length);
      const button = screen.getByRole("button", { name: "Log out" });
      expect(button).toBeEnabled();

      await user.click(button);

      expect(
        await screen.findByRole("button", { name: "Logging out..." }),
      ).toBeDisabled();
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
      nextAttempt.finish(204);
      await waitFor(() =>
        expect(router.state.location.pathname).toBe("/login"),
      );
    },
  );
});
