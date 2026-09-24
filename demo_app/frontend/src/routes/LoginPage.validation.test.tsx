import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { api, demoUser } from "../test/handlers";
import { renderApp } from "../test/renderApp";
import { server } from "../test/server";

const fields = {
  username: { label: "Username", error: "Username is required" },
  password: { label: "Password", error: "Password is required" },
} as const;

describe("/login submit-deferred validation", () => {
  let loginRequests: number;

  beforeEach(() => {
    loginRequests = 0;
    server.use(
      http.post(api("/api/v1/auth/login"), () => {
        loginRequests += 1;
        return HttpResponse.json(demoUser);
      }),
    );
  });

  async function openLogin() {
    const app = renderApp("/login");
    await screen.findByLabelText("Username");
    return app;
  }

  const submit = () => screen.getByRole("button", { name: "Log in" });

  it("shows 'Username is required' under a blank username and sends nothing", async () => {
    const { user } = await openLogin();

    await user.type(screen.getByLabelText("Password"), "Password123!");
    await user.click(submit());

    const username = screen.getByLabelText("Username");
    expect(username).toHaveAttribute("aria-invalid", "true");
    expect(username).toHaveAccessibleDescription("Username is required");
    expect(screen.getByLabelText("Password")).not.toHaveAttribute(
      "aria-invalid",
    );
    expect(screen.queryByText("Password is required")).not.toBeInTheDocument();
    expect(loginRequests).toBe(0);
  });

  it("shows 'Password is required' under a blank password and sends nothing", async () => {
    const { user } = await openLogin();

    await user.type(screen.getByLabelText("Username"), "johndoe");
    await user.click(submit());

    const password = screen.getByLabelText("Password");
    expect(password).toHaveAttribute("aria-invalid", "true");
    expect(password).toHaveAccessibleDescription("Password is required");
    expect(screen.getByLabelText("Username")).not.toHaveAttribute(
      "aria-invalid",
    );
    expect(screen.queryByText("Username is required")).not.toBeInTheDocument();
    expect(loginRequests).toBe(0);
  });

  it("shows both messages when both fields are blank and sends nothing", async () => {
    const { user } = await openLogin();

    await user.click(submit());

    expect(screen.getByLabelText("Username")).toHaveAccessibleDescription(
      "Username is required",
    );
    expect(screen.getByLabelText("Password")).toHaveAccessibleDescription(
      "Password is required",
    );
    expect(loginRequests).toBe(0);
  });

  it("treats whitespace-only input as blank", async () => {
    const { user } = await openLogin();

    await user.type(screen.getByLabelText("Username"), "   ");
    await user.type(screen.getByLabelText("Password"), "  ");
    await user.click(submit());

    expect(screen.getByText("Username is required")).toBeInTheDocument();
    expect(screen.getByText("Password is required")).toBeInTheDocument();
    expect(loginRequests).toBe(0);
  });

  it("shows no inline errors or banner while typing before the first submit", async () => {
    const { user } = await openLogin();

    await user.type(screen.getByLabelText("Username"), " <script>!@#$%^&*()");
    await user.clear(screen.getByLabelText("Username"));
    await user.type(screen.getByLabelText("Password"), " éß☃ ");
    await user.clear(screen.getByLabelText("Password"));

    expect(screen.queryByText(/is required/)).not.toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    for (const { label } of Object.values(fields)) {
      expect(screen.getByLabelText(label)).not.toHaveAttribute("aria-invalid");
      expect(screen.getByLabelText(label)).not.toHaveAccessibleDescription();
    }
    expect(loginRequests).toBe(0);
  });

  it.each([
    ["username", "password"],
    ["password", "username"],
  ] as const)(
    "typing one character into %s dismisses only its error",
    async (typed, other) => {
      const { user } = await openLogin();
      await user.click(submit());

      await user.type(screen.getByLabelText(fields[typed].label), "!");

      expect(screen.queryByText(fields[typed].error)).not.toBeInTheDocument();
      expect(screen.getByLabelText(fields[typed].label)).not.toHaveAttribute(
        "aria-invalid",
      );
      expect(
        screen.getByLabelText(fields[other].label),
      ).toHaveAccessibleDescription(fields[other].error);
      expect(screen.getByLabelText(fields[other].label)).toHaveAttribute(
        "aria-invalid",
        "true",
      );
    },
  );

  it("sends the login once the blank fields are filled in", async () => {
    const { user, router } = await openLogin();
    await user.click(submit());

    await user.type(screen.getByLabelText("Username"), "johndoe");
    await user.type(screen.getByLabelText("Password"), "Password123!");
    await user.click(submit());

    await waitFor(() => expect(router.state.location.pathname).toBe("/"));
    expect(loginRequests).toBe(1);
  });
});
