import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { api, csrfToken, demoUser } from "../test/handlers";
import { renderApp } from "../test/renderApp";
import { server } from "../test/server";

const VALID = {
  username: "NewUser",
  email: "new.user@example.com",
  firstName: "Ada",
  password: "correct horse battery",
};

/** Records every registration request and answers each with `reply`. */
function captureRegistrations(reply?: () => Response) {
  const sent: { body: unknown; csrf: string | null }[] = [];
  server.use(
    http.post(api("/api/v1/auth/register"), async ({ request }) => {
      sent.push({
        body: await request.json(),
        csrf: request.headers.get("X-XSRF-TOKEN"),
      });
      return (
        reply?.() ??
        HttpResponse.json(
          { username: "newuser", firstName: "Ada", role: "USER" },
          { status: 201 },
        )
      );
    }),
  );
  return sent;
}

function apiError(status: number, code: string, fieldErrors: object[] = []) {
  return HttpResponse.json(
    { status, code, message: "Some fields are invalid", fieldErrors },
    { status },
  );
}

async function openRegister() {
  const app = renderApp("/register");
  await screen.findByRole("heading", { name: "Create an account" });
  return app;
}

type Values = Partial<typeof VALID & { confirmPassword: string }>;

async function fillIn(
  user: Awaited<ReturnType<typeof openRegister>>["user"],
  values: Values = {},
) {
  const all = { ...VALID, confirmPassword: VALID.password, ...values };
  const labels = {
    username: "Username",
    email: "Email",
    firstName: "First name",
    password: "Password",
    confirmPassword: "Confirm password",
  } as const;
  for (const [field, label] of Object.entries(labels)) {
    const value = all[field as keyof typeof all];
    if (value) await user.type(screen.getByLabelText(label), value);
  }
}

const submit = () => screen.getByRole("button", { name: "Create account" });

describe("/register", () => {
  it("is reachable from the login page's 'Create an account' link, and links back", async () => {
    const { user, router } = renderApp("/login");

    await user.click(
      await screen.findByRole("link", { name: "Create an account" }),
    );
    await screen.findByRole("heading", { name: "Create an account" });
    expect(router.state.location.pathname).toBe("/register");

    await user.click(screen.getByRole("link", { name: "Log in" }));
    await screen.findByRole("heading", { name: "Log in" });
    expect(router.state.location.pathname).toBe("/login");
  });

  it("registers, then lands on /login with the one-shot notice", async () => {
    const sent = captureRegistrations();
    const { user, router } = await openRegister();

    await fillIn(user, {
      email: "  new.user@example.com ",
      firstName: " Ada ",
    });
    await user.click(submit());

    expect(
      await screen.findByText("Account created. Please log in."),
    ).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(
      "Account created. Please log in.",
    );
    await waitFor(() => expect(router.state.location.searchStr).toBe(""));
    expect(router.state.location.pathname).toBe("/login");
    // The confirmation never leaves the browser; the API trims too, but the form sends clean values.
    expect(sent).toEqual([{ body: VALID, csrf: csrfToken }]);
  });

  it("catches a mismatched confirmation on submit and sends nothing", async () => {
    const sent = captureRegistrations();
    const { user } = await openRegister();

    await fillIn(user, { confirmPassword: "correct horse batterY" });
    await user.click(submit());

    const confirm = screen.getByLabelText("Confirm password");
    expect(confirm).toHaveAttribute("aria-invalid", "true");
    expect(confirm).toHaveAccessibleDescription("Passwords do not match");
    expect(confirm).toHaveFocus();
    expect(sent).toEqual([]);
  });

  it("names every blank field, focuses the first, and sends nothing", async () => {
    const sent = captureRegistrations();
    const { user } = await openRegister();

    await user.click(submit());

    const expected = {
      Username: "Username is required",
      Email: "Email is required",
      "First name": "First name is required",
      Password: "Password is required",
      "Confirm password": "Please confirm your password",
    };
    for (const [label, message] of Object.entries(expected)) {
      expect(screen.getByLabelText(label)).toHaveAccessibleDescription(message);
    }
    expect(screen.getByLabelText("Username")).toHaveFocus();
    expect(sent).toEqual([]);
  });

  it.each([
    ["Username", { username: "ab" }, /^Username must be 3 to 50/],
    ["Username", { username: "has space" }, /^Username must be 3 to 50/],
    ["Email", { email: "not-an-email" }, /^Enter a valid email address/],
    [
      "First name",
      { firstName: "x".repeat(101) },
      /^First name must be 1 to 100/,
    ],
    [
      "Password",
      { password: "only11chars", confirmPassword: "only11chars" },
      /^Password must be 12 to 64 characters/,
    ],
    [
      "Password",
      { password: "x".repeat(65), confirmPassword: "x".repeat(65) },
      /^Password must be 12 to 64 characters/,
    ],
    ["First name", { firstName: "   " }, /^First name is required/],
  ] as const)(
    "rejects an invalid %s before sending (%o)",
    async (label, values, message) => {
      const sent = captureRegistrations();
      const { user } = await openRegister();

      await fillIn(user, values);
      await user.click(submit());

      expect(screen.getByLabelText(label)).toHaveAccessibleDescription(message);
      expect(screen.getByLabelText(label)).toHaveFocus();
      expect(sent).toEqual([]);
    },
  );

  it("counts password length in characters, as the API does", async () => {
    const sent = captureRegistrations();
    const { user } = await openRegister();
    // 12 emoji: 24 UTF-16 units, but 12 characters.
    const emoji = "\u{1F512}".repeat(12);

    await fillIn(user, { password: emoji, confirmPassword: emoji });
    await user.click(submit());

    await waitFor(() => expect(sent).toHaveLength(1));
  });

  it("shows a taken username (409) under the username field and focuses it", async () => {
    captureRegistrations(() =>
      apiError(409, "ACCOUNT_CONFLICT", [
        { field: "username", message: "This username is already taken." },
      ]),
    );
    const { user, router } = await openRegister();

    await fillIn(user, { username: "johndoe" });
    await user.click(submit());

    const username = screen.getByLabelText("Username");
    await waitFor(() =>
      expect(username).toHaveAccessibleDescription(
        "This username is already taken.",
      ),
    );
    await waitFor(() => expect(username).toHaveFocus());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/register");
  });

  it("maps several server field errors, ignoring fields the form doesn't have", async () => {
    captureRegistrations(() =>
      apiError(409, "ACCOUNT_CONFLICT", [
        { field: "username", message: "This username is already taken." },
        {
          field: "email",
          message: "An account with this email already exists.",
        },
        { field: "role", message: "Not a form field" },
      ]),
    );
    const { user } = await openRegister();

    await fillIn(user);
    await user.click(submit());

    expect(
      await screen.findByText("An account with this email already exists."),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Username")).toHaveAccessibleDescription(
      "This username is already taken.",
    );
    expect(screen.queryByText("Not a form field")).not.toBeInTheDocument();
  });

  it("shows the password policy error from the API (e.g. a common password)", async () => {
    captureRegistrations(() =>
      apiError(400, "VALIDATION_FAILED", [
        {
          field: "password",
          message: "This password is too common. Choose a less guessable one.",
        },
      ]),
    );
    const { user } = await openRegister();

    await fillIn(user, {
      password: "unbelievable",
      confirmPassword: "unbelievable",
    });
    await user.click(submit());

    await waitFor(() =>
      expect(screen.getByLabelText("Password")).toHaveAccessibleDescription(
        "This password is too common. Choose a less guessable one.",
      ),
    );
  });

  it("typing into a field clears only its error", async () => {
    const { user } = await openRegister();
    await user.click(submit());

    await user.type(screen.getByLabelText("Email"), "a");

    expect(screen.getByLabelText("Email")).not.toHaveAttribute("aria-invalid");
    expect(screen.getByLabelText("Username")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
  });

  it.each([
    [
      "a throttled client",
      () =>
        HttpResponse.json(
          { code: "TOO_MANY_REQUESTS", message: "Too many attempts." },
          { status: 429 },
        ),
      "Too many attempts. Please try again later.",
    ],
    [
      "a rejection without field errors",
      () =>
        HttpResponse.json(
          { code: "PAYLOAD_TOO_LARGE", message: "Request body too large" },
          { status: 413 },
        ),
      "Request body too large",
    ],
    [
      "a server error",
      () => HttpResponse.json({ message: "Boom" }, { status: 500 }),
      "Unable to connect to the server. Please try again later.",
    ],
    [
      "a network failure",
      () => HttpResponse.error(),
      "Unable to connect to the server. Please try again later.",
    ],
  ])("shows a banner for %s", async (_, reply, message) => {
    captureRegistrations(reply);
    const { user, router } = await openRegister();

    await fillIn(user);
    await user.click(submit());

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(submit()).toBeEnabled();
    expect(router.state.location.pathname).toBe("/register");
  });

  it("sends a signed-in user to the landing page", async () => {
    server.use(
      http.get(api("/api/v1/auth/me"), () => HttpResponse.json(demoUser)),
    );
    const { router } = renderApp("/register");

    expect(
      await screen.findByRole("heading", { name: "Hello, John!" }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/");
    expect(
      screen.queryByRole("heading", { name: "Create an account" }),
    ).not.toBeInTheDocument();
  });
});
