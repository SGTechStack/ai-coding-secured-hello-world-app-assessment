import { defaultScheduler, notifyManager } from "@tanstack/react-query";
import { act, fireEvent, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, csrfResponse, csrfToken, demoUser } from "../test/handlers";
import { renderApp } from "../test/renderApp";
import { server } from "../test/server";

describe("/login", () => {
  it("renders the login form", async () => {
    renderApp("/login");

    expect(await screen.findByLabelText("Username")).toBeInTheDocument();
    expect(screen.getByLabelText("Password")).toHaveAttribute(
      "type",
      "password",
    );
    expect(screen.getByRole("button", { name: "Log in" })).toBeEnabled();
  });

  it("logs in with valid credentials and navigates to /", async () => {
    const sent: { body: unknown; csrf: string | null }[] = [];
    server.use(
      http.post(api("/api/v1/auth/login"), async ({ request }) => {
        sent.push({
          body: await request.json(),
          csrf: request.headers.get("X-XSRF-TOKEN"),
        });
        return HttpResponse.json(demoUser);
      }),
    );
    const { user, router } = renderApp("/login");

    await user.type(await screen.findByLabelText("Username"), "johndoe");
    await user.type(screen.getByLabelText("Password"), "Password123!");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    await waitFor(() => expect(router.state.location.pathname).toBe("/"));
    expect(sent).toEqual([
      {
        body: { username: "johndoe", password: "Password123!" },
        csrf: csrfToken,
      },
    ]);
  });

  describe("when the login is rejected with a 403", () => {
    /** Counts CSRF primes and answers each login with the next status in turn. */
    function respondToLoginWith(...statuses: number[]) {
      const counts = { primes: 0, logins: 0 };
      server.use(
        http.get(api("/api/v1/auth/csrf"), () => {
          counts.primes += 1;
          return csrfResponse();
        }),
        http.post(api("/api/v1/auth/login"), () => {
          const status = statuses[counts.logins] ?? 200;
          counts.logins += 1;
          return status === 200
            ? HttpResponse.json(demoUser)
            : HttpResponse.json({ message: "Forbidden" }, { status });
        }),
      );
      return counts;
    }

    async function submitValidLogin() {
      const app = renderApp("/login");
      await app.user.type(await screen.findByLabelText("Username"), "johndoe");
      await app.user.type(screen.getByLabelText("Password"), "Password123!");
      await app.user.click(screen.getByRole("button", { name: "Log in" }));
      return app;
    }

    it("re-primes the CSRF token and retries once, then logs in", async () => {
      const counts = respondToLoginWith(403, 200);

      const { router } = await submitValidLogin();

      await waitFor(() => expect(router.state.location.pathname).toBe("/"));
      expect(counts).toEqual({ primes: 2, logins: 2 });
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("shows the can't-connect banner when the retry is rejected too", async () => {
      const counts = respondToLoginWith(403, 403);

      const { router } = await submitValidLogin();

      expect(await screen.findByRole("alert")).toHaveTextContent(
        "Unable to connect to the server. Please try again later.",
      );
      expect(counts).toEqual({ primes: 2, logins: 2 });
      expect(router.state.location.pathname).toBe("/login");
      expect(screen.getByRole("button", { name: "Log in" })).toBeEnabled();
    });
  });
});

describe("/login submission feedback", () => {
  const realSetTimeout = globalThis.setTimeout;
  const invalidCredentials = "Invalid username or password";
  const unavailable =
    "Unable to connect to the server. Please try again later.";

  // React Query batches re-renders on setTimeout(0); run them as microtasks instead so fake
  // time measures only the form's own 400ms minimum.
  beforeEach(() => {
    notifyManager.setScheduler(queueMicrotask);
  });

  afterEach(() => {
    vi.useRealTimers();
    notifyManager.setScheduler(defaultScheduler);
  });

  /**
   * Renders /login and fills in the form with real timers, then fakes setTimeout so the 400ms
   * minimum can be stepped through. userEvent must not run while timers are fake (RTL's async
   * wrapper waits on a real setTimeout), so submit with fireEvent and call `vi.useRealTimers()`
   * before typing again.
   */
  async function renderFilledLogin(password = "Password123!") {
    const { user, router } = renderApp("/login");
    await user.type(await screen.findByLabelText("Username"), "johndoe");
    await user.type(screen.getByLabelText("Password"), password);
    vi.useFakeTimers({ toFake: ["setTimeout", "clearTimeout"] });
    return { user, router };
  }

  async function submit() {
    fireEvent.click(screen.getByRole("button", { name: "Log in" }));
    await letNetworkSettle();
    await advance(0);
  }

  async function advance(ms: number) {
    await act(() => vi.advanceTimersByTimeAsync(ms));
  }

  /**
   * MSW and fetch complete on the real event loop, which fake time does not drive. Waiting a
   * little real time (while fake time stands still) guarantees an "instant" response has been
   * fully received before the test steps through the 400ms.
   */
  async function letNetworkSettle() {
    await act(() => new Promise((resolve) => realSetTimeout(resolve, 50)));
  }

  function respondToLogin(response: () => Response) {
    server.use(http.post(api("/api/v1/auth/login"), response));
  }

  function expectSubmitting() {
    expect(screen.getByLabelText("Username")).toBeDisabled();
    expect(screen.getByLabelText("Password")).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "Logging in..." }),
    ).toBeDisabled();
  }

  function expectReEnabled() {
    expect(screen.getByLabelText("Username")).toBeEnabled();
    expect(screen.getByLabelText("Password")).toBeEnabled();
    expect(screen.getByRole("button", { name: "Log in" })).toBeEnabled();
  }

  it("stays in the loading state for at least 400ms when the response is instant", async () => {
    respondToLogin(() =>
      HttpResponse.json({ message: invalidCredentials }, { status: 401 }),
    );
    await renderFilledLogin("wrong-password");

    await submit();
    expectSubmitting();

    await advance(399);
    expectSubmitting();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();

    await advance(1);
    expect(screen.getByRole("alert")).toHaveTextContent(invalidCredentials);
    expectReEnabled();
  });

  it("waits the 400ms minimum before navigating after a successful login", async () => {
    const { router } = await renderFilledLogin();

    await submit();
    await advance(399);
    expectSubmitting();
    expect(router.state.location.pathname).toBe("/login");

    await advance(1);
    expect(router.state.location.pathname).toBe("/");
  });

  it("stays in the loading state until a slow response settles", async () => {
    let respond: () => void = () => {};
    const settled = new Promise<void>((resolve) => (respond = resolve));
    server.use(
      http.post(api("/api/v1/auth/login"), async () => {
        await settled;
        return HttpResponse.json({ message: "boom" }, { status: 500 });
      }),
    );
    await renderFilledLogin();

    await submit();
    await advance(1000);
    expectSubmitting();

    respond();
    await letNetworkSettle();
    await advance(0);
    expect(screen.getByRole("alert")).toHaveTextContent(unavailable);
    expectReEnabled();
  });

  it("shows a generic banner on 401 that does not point at either field", async () => {
    respondToLogin(() =>
      HttpResponse.json({ message: invalidCredentials }, { status: 401 }),
    );
    await renderFilledLogin("wrong-password");

    await submit();
    await advance(400);

    expect(screen.getAllByRole("alert")).toHaveLength(1);
    expect(screen.getByRole("alert")).toHaveTextContent(
      /^Invalid username or password$/,
    );
    for (const label of ["Username", "Password"]) {
      expect(screen.getByLabelText(label)).not.toHaveAttribute(
        "aria-invalid",
        "true",
      );
    }
    expectReEnabled();
  });

  it("shows the throttle message on a 429, not the invalid-credentials one", async () => {
    respondToLogin(() =>
      HttpResponse.json(
        {
          status: 429,
          code: "TOO_MANY_REQUESTS",
          message: "Too many attempts. Please try again later.",
        },
        { status: 429, headers: { "Retry-After": "900" } },
      ),
    );
    await renderFilledLogin();

    await submit();
    await advance(400);

    expect(screen.getAllByRole("alert")).toHaveLength(1);
    expect(screen.getByRole("alert")).toHaveTextContent(
      /^Too many attempts\. Please try again later\.$/,
    );
    expectReEnabled();
  });

  it.each([
    ["a 5xx response", () => new HttpResponse(null, { status: 503 })],
    ["a network failure", () => HttpResponse.error()],
  ])("shows the can't-connect banner on %s", async (_, response) => {
    respondToLogin(response);
    await renderFilledLogin();

    await submit();
    await advance(400);

    expect(screen.getByRole("alert")).toHaveTextContent(unavailable);
    expectReEnabled();
  });

  it.each([
    [401, "Username"],
    [401, "Password"],
    [500, "Username"],
    [500, "Password"],
  ])(
    "dismisses the banner after a %i when typing in the %s field",
    async (status, label) => {
      respondToLogin(() =>
        HttpResponse.json({ message: "failed" }, { status }),
      );
      const { user } = await renderFilledLogin();
      await submit();
      await advance(400);
      expect(screen.getByRole("alert")).toBeInTheDocument();
      vi.useRealTimers();

      await user.type(screen.getByLabelText(label), "x");

      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    },
  );
});
