import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { api, csrfResponse, csrfToken, demoUser } from "../test/handlers";
import { renderApp } from "../test/renderApp";
import { server } from "../test/server";

const GENERIC = "If an account exists for that email, we've sent a reset link.";
const INVALID = "This reset link is invalid or has expired.";
const UPDATED = "Password updated. Please log in.";
const NEW_PASSWORD = "stapled paper clip";

function signedIn() {
  server.use(
    http.get(api("/api/v1/auth/me"), () => HttpResponse.json(demoUser)),
  );
}

/** Records reset requests' bodies and answers each with `reply` (default: the empty 202). */
function captureResetRequests(reply?: () => Response) {
  const sent: unknown[] = [];
  server.use(
    http.post(
      api("/api/v1/auth/password-reset/request"),
      async ({ request }) => {
        sent.push(await request.json());
        return reply?.() ?? new HttpResponse(null, { status: 202 });
      },
    ),
  );
  return sent;
}

/** Records confirm bodies and CSRF headers, answering each with `reply` (default: 204). */
function captureConfirms(reply?: () => Response) {
  const sent: { body: unknown; csrf: string | null }[] = [];
  server.use(
    http.post(
      api("/api/v1/auth/password-reset/confirm"),
      async ({ request }) => {
        sent.push({
          body: await request.json(),
          csrf: request.headers.get("X-XSRF-TOKEN"),
        });
        return reply?.() ?? new HttpResponse(null, { status: 204 });
      },
    ),
  );
  return sent;
}

function invalidToken() {
  return HttpResponse.json(
    { status: 400, code: "INVALID_RESET_TOKEN", message: INVALID },
    { status: 400 },
  );
}

describe("/forgot-password", () => {
  it("is reachable from the login page's 'Forgot password?' link", async () => {
    const { user, router } = renderApp("/login");

    await user.click(
      await screen.findByRole("link", { name: "Forgot password?" }),
    );

    await screen.findByRole("heading", { name: "Forgot password" });
    expect(router.state.location.pathname).toBe("/forgot-password");
  });

  it.each([
    ["the empty 202", () => new HttpResponse(null, { status: 202 })],
    [
      "a 400",
      () => HttpResponse.json({ code: "MALFORMED_REQUEST" }, { status: 400 }),
    ],
    [
      "a 429",
      () => HttpResponse.json({ code: "TOO_MANY_REQUESTS" }, { status: 429 }),
    ],
    ["a 500", () => HttpResponse.json({}, { status: 500 })],
  ])("shows the generic message for %s", async (_, reply) => {
    const sent = captureResetRequests(reply);
    const { user } = renderApp("/forgot-password");

    await user.type(
      await screen.findByLabelText("Email"),
      " Someone@Example.com ",
    );
    await user.click(screen.getByRole("button", { name: "Send reset link" }));

    expect(await screen.findByRole("status")).toHaveTextContent(GENERIC);
    expect(sent).toEqual([{ email: "Someone@Example.com" }]);
  });

  it("says it can't connect when there is no answer at all", async () => {
    captureResetRequests(() => HttpResponse.error());
    const { user } = renderApp("/forgot-password");

    await user.type(await screen.findByLabelText("Email"), "a@example.com");
    await user.click(screen.getByRole("button", { name: "Send reset link" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Unable to connect to the server",
    );
    expect(screen.queryByText(GENERIC)).not.toBeInTheDocument();
  });

  it("asks for an email before sending anything", async () => {
    const sent = captureResetRequests();
    const { user } = renderApp("/forgot-password");

    await user.click(
      await screen.findByRole("button", { name: "Send reset link" }),
    );

    const email = screen.getByLabelText("Email");
    expect(email).toHaveAccessibleDescription("Email is required");
    expect(email).toHaveFocus();
    expect(sent).toEqual([]);

    await user.type(email, "a");
    expect(email).not.toHaveAttribute("aria-invalid");
  });

  it("sends a signed-in user to /", async () => {
    signedIn();
    const { router } = renderApp("/forgot-password");

    await screen.findByRole("heading", { name: "Hello, John!" });
    expect(router.state.location.pathname).toBe("/");
  });
});

describe("/reset-password", () => {
  async function openWithToken(token = "the-token") {
    const app = renderApp(`/reset-password#token=${token}`);
    await screen.findByRole("heading", { name: "Set a new password" });
    return app;
  }

  async function fillIn(
    user: Awaited<ReturnType<typeof openWithToken>>["user"],
    password = NEW_PASSWORD,
    confirm = password,
  ) {
    await user.type(screen.getByLabelText("New password"), password);
    await user.type(screen.getByLabelText("Confirm new password"), confirm);
    await user.click(screen.getByRole("button", { name: "Update password" }));
  }

  it("reads the token from the hash, removes the hash, and sends the token on submit", async () => {
    const sent = captureConfirms();
    const { user, router } = await openWithToken("abc_DEF-123");

    await waitFor(() => expect(router.state.location.hash).toBe(""));
    expect(router.state.location.href).toBe("/reset-password");

    await fillIn(user);

    await waitFor(() => expect(router.state.location.pathname).toBe("/login"));
    expect(sent).toEqual([
      {
        body: { token: "abc_DEF-123", newPassword: NEW_PASSWORD },
        csrf: csrfToken,
      },
    ]);
    expect(await screen.findByRole("status")).toHaveTextContent(UPDATED);
  });

  it("drops the CSRF token after a successful reset", async () => {
    let csrfFetches = 0;
    server.use(
      http.get(api("/api/v1/auth/csrf"), () => {
        csrfFetches += 1;
        return csrfResponse();
      }),
    );
    captureConfirms();
    const { user } = await openWithToken();
    await fillIn(user);
    await screen.findByRole("status");
    expect(csrfFetches).toBe(1);

    // The next state-changing request fetches a fresh token.
    await user.type(screen.getByLabelText("Username"), "johndoe");
    await user.type(screen.getByLabelText("Password"), "Password123!");
    await user.click(screen.getByRole("button", { name: "Log in" }));
    await waitFor(() => expect(csrfFetches).toBe(2));
  });

  it("shows the invalid-link message with a link to request a new one", async () => {
    captureConfirms(invalidToken);
    const { user, router } = await openWithToken();

    await fillIn(user);

    expect(await screen.findByRole("alert")).toHaveTextContent(INVALID);
    await user.click(
      screen.getByRole("link", { name: "Request a new reset link" }),
    );
    await screen.findByRole("heading", { name: "Forgot password" });
    expect(router.state.location.pathname).toBe("/forgot-password");
  });

  it("shows the invalid-link message straight away when there is no token", async () => {
    const sent = captureConfirms();
    renderApp("/reset-password");

    expect(await screen.findByRole("alert")).toHaveTextContent(INVALID);
    expect(screen.queryByLabelText("New password")).not.toBeInTheDocument();
    expect(sent).toEqual([]);
  });

  it("catches a short or mismatched password before sending", async () => {
    const sent = captureConfirms();
    const { user } = await openWithToken();

    await fillIn(user, "short", "short");
    const password = screen.getByLabelText("New password");
    expect(password).toHaveAccessibleDescription(
      "Password must be 12 to 64 characters.",
    );
    await waitFor(() => expect(password).toHaveFocus());

    await user.clear(password);
    await user.type(password, NEW_PASSWORD);
    await user.clear(screen.getByLabelText("Confirm new password"));
    await user.type(
      screen.getByLabelText("Confirm new password"),
      "something else",
    );
    await user.click(screen.getByRole("button", { name: "Update password" }));
    expect(
      screen.getByLabelText("Confirm new password"),
    ).toHaveAccessibleDescription("Passwords do not match");
    expect(sent).toEqual([]);
  });

  it("puts the API's password policy error on the field, keeping the form", async () => {
    captureConfirms(() =>
      HttpResponse.json(
        {
          status: 400,
          code: "VALIDATION_FAILED",
          message: "Some fields are invalid",
          fieldErrors: [
            { field: "newPassword", message: "This password is too common." },
          ],
        },
        { status: 400 },
      ),
    );
    const { user } = await openWithToken();

    await fillIn(user, "Unbelievable");

    const password = screen.getByLabelText("New password");
    await waitFor(() =>
      expect(password).toHaveAccessibleDescription(
        "This password is too common.",
      ),
    );
    await waitFor(() => expect(password).toHaveFocus());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it.each([
    [
      "a 429",
      () => HttpResponse.json({ code: "TOO_MANY_REQUESTS" }, { status: 429 }),
      "Too many attempts. Please try again later.",
    ],
    [
      "a 500",
      () => HttpResponse.json({}, { status: 500 }),
      "Unable to connect",
    ],
    [
      "another 400",
      () =>
        HttpResponse.json(
          { code: "MALFORMED_REQUEST", message: "Malformed request" },
          { status: 400 },
        ),
      "Malformed request",
    ],
  ])("shows a banner for %s", async (_, reply, message) => {
    captureConfirms(reply);
    const { user } = await openWithToken();

    await fillIn(user);

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.getByLabelText("New password")).toBeInTheDocument();
  });

  it("sends a signed-in user to /", async () => {
    signedIn();
    const { router } = renderApp("/reset-password#token=abc");

    await screen.findByRole("heading", { name: "Hello, John!" });
    expect(router.state.location.pathname).toBe("/");
  });
});
