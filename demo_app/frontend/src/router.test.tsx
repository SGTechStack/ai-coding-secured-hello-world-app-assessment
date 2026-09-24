import { screen, waitFor } from "@testing-library/react";
import { delay, http, HttpResponse } from "msw";
import { afterEach, describe, expect, it } from "vitest";
import { api, demoUser } from "./test/handlers";
import { renderApp } from "./test/renderApp";
import { server } from "./test/server";

/**
 * Records whether text matching `pattern` ever appears in the document, including in renders a
 * later redirect replaces, so a test can assert a page "never rendered".
 */
function watchForText(pattern: RegExp) {
  let seen = false;
  const observer = new MutationObserver(() => {
    if (pattern.test(document.body.textContent ?? "")) seen = true;
  });
  observer.observe(document.body, {
    childList: true,
    subtree: true,
    characterData: true,
  });
  observers.push(observer);
  return () => seen;
}

const observers: MutationObserver[] = [];
afterEach(() => {
  observers.splice(0).forEach((observer) => observer.disconnect());
});

describe("route guards", () => {
  describe("/ (requires a session)", () => {
    it("redirects to /login when /me says there is no session", async () => {
      const greetingSeen = watchForText(/Hello/);
      const { router } = renderApp("/");

      expect(await screen.findByLabelText("Username")).toBeInTheDocument();
      expect(router.state.location.pathname).toBe("/login");
      expect(greetingSeen()).toBe(false);
    });

    it("treats a network failure from /me as no session", async () => {
      server.use(http.get(api("/api/v1/auth/me"), () => HttpResponse.error()));
      const { router } = renderApp("/");

      expect(await screen.findByLabelText("Username")).toBeInTheDocument();
      expect(router.state.location.pathname).toBe("/login");
    });

    it("treats a server error from /me as no session", async () => {
      server.use(
        http.get(api("/api/v1/auth/me"), () =>
          HttpResponse.json({ message: "Boom" }, { status: 500 }),
        ),
      );
      const { router } = renderApp("/");

      expect(await screen.findByLabelText("Username")).toBeInTheDocument();
      expect(router.state.location.pathname).toBe("/login");
    });

    it("stays on / when /me reports a session", async () => {
      server.use(
        http.get(api("/api/v1/auth/me"), () => HttpResponse.json(demoUser)),
      );
      const { router } = renderApp("/");

      expect(
        await screen.findByRole("heading", { name: "Hello, John!" }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe("/");
    });
  });

  describe("/login (requires no session)", () => {
    it("redirects to / when /me reports a session, without rendering the form", async () => {
      server.use(
        http.get(api("/api/v1/auth/me"), () => HttpResponse.json(demoUser)),
      );
      const formSeen = watchForText(/Log in|Username|Password/);
      const { router } = renderApp("/login");

      expect(
        await screen.findByRole("heading", { name: "Hello, John!" }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe("/");
      expect(formSeen()).toBe(false);
    });

    it("renders nothing while the session check is still in flight", async () => {
      let meRequested = false;
      server.use(
        http.get(api("/api/v1/auth/me"), async () => {
          meRequested = true;
          await delay("infinite");
          return HttpResponse.json(demoUser);
        }),
      );
      renderApp("/login");

      await waitFor(() => expect(meRequested).toBe(true));
      expect(screen.queryByLabelText("Username")).not.toBeInTheDocument();
      expect(screen.queryByRole("heading")).not.toBeInTheDocument();
    });

    it("shows the login form when the session check fails", async () => {
      server.use(http.get(api("/api/v1/auth/me"), () => HttpResponse.error()));
      const { router } = renderApp("/login");

      expect(await screen.findByLabelText("Username")).toBeInTheDocument();
      expect(router.state.location.pathname).toBe("/login");
    });
  });
});
