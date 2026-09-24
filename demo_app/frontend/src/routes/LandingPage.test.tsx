import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { api, demoUser } from "../test/handlers";
import { renderApp } from "../test/renderApp";
import { server } from "../test/server";

describe("/ (landing page)", () => {
  it("greets a user with a live session by first name on a fresh load", async () => {
    server.use(
      http.get(api("/api/v1/auth/me"), () => HttpResponse.json(demoUser)),
    );
    const { router } = renderApp("/");

    expect(
      await screen.findByRole("heading", { name: "Hello, John!" }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/");
  });

  it("greets by whatever first name the session reports", async () => {
    server.use(
      http.get(api("/api/v1/auth/me"), () =>
        HttpResponse.json({
          username: "janedoe",
          firstName: "Jane",
          role: "USER",
        }),
      ),
    );
    renderApp("/");

    expect(
      await screen.findByRole("heading", { name: "Hello, Jane!" }),
    ).toBeInTheDocument();
  });

  it("greets the user right after login from the cached profile, without asking /me", async () => {
    let meRequests = 0;
    server.use(
      http.get(api("/api/v1/auth/me"), () => {
        meRequests += 1;
        return HttpResponse.json({ message: "Unauthorized" }, { status: 401 });
      }),
    );
    const { user, router } = renderApp("/login");

    await user.type(await screen.findByLabelText("Username"), "johndoe");
    // The /login guard asked /me once before showing the form; only count what follows login.
    meRequests = 0;
    await user.type(screen.getByLabelText("Password"), "Password123!");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    expect(
      await screen.findByRole("heading", { name: "Hello, John!" }),
    ).toBeInTheDocument();
    await waitFor(() => expect(router.state.location.pathname).toBe("/"));
    expect(meRequests).toBe(0);
  });

  it("shows an HTML-looking first name as literal text, never as markup", async () => {
    const firstName = "<img src=x onerror=alert(1)>";
    server.use(
      http.get(api("/api/v1/auth/me"), () =>
        HttpResponse.json({ username: "mallory", firstName, role: "USER" }),
      ),
    );
    renderApp("/");

    expect(
      await screen.findByRole("heading", { name: `Hello, ${firstName}!` }),
    ).toBeInTheDocument();
    expect(document.querySelector("img")).toBeNull();
  });
});
