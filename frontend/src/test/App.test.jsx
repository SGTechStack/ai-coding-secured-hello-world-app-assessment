import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import App from "../App.jsx";

beforeEach(() => {
  window.history.pushState({}, "", "/");
});

afterEach(() => vi.restoreAllMocks());

// Routes fetch by URL so the /api/me probe, greeting, and admin list can each
// be answered independently.
function mockApi({ me }) {
  vi.stubGlobal(
    "fetch",
    vi.fn((url) => {
      const u = String(url);
      if (u.includes("/api/me")) {
        return Promise.resolve(
          me
            ? { ok: true, status: 200, json: async () => me }
            : { ok: false, status: 401, json: async () => ({}) },
        );
      }
      if (u.includes("/api/hello")) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: async () => ({ message: `Hello, ${me?.username ?? "x"}` }),
        });
      }
      if (u.includes("/api/admin/users")) {
        return Promise.resolve({ ok: true, status: 200, json: async () => [] });
      }
      return Promise.resolve({ ok: true, status: 200, json: async () => ({}) });
    }),
  );
}

test("logged out: shows the login form when /api/me returns 401", async () => {
  mockApi({ me: null });
  render(<App />);
  await waitFor(() =>
    expect(screen.getByRole("region", { name: /login/i })).toBeInTheDocument(),
  );
  expect(screen.getByRole("button", { name: /create an account/i })).toBeInTheDocument();
});

test("USER: greeting + nav, but NO admin nav item", async () => {
  mockApi({ me: { username: "alice", role: "USER" } });
  render(<App />);
  await waitFor(() =>
    expect(screen.getByTestId("greeting")).toHaveTextContent("Hello, alice"),
  );
  expect(screen.getByRole("navigation", { name: /main/i })).toBeInTheDocument();
  expect(screen.queryByTestId("nav-admin")).not.toBeInTheDocument();
});

test("ADMIN: admin nav item appears and navigates to the user-management page", async () => {
  mockApi({ me: { username: "boss", role: "ADMIN" } });
  render(<App />);
  await waitFor(() => expect(screen.getByTestId("nav-admin")).toBeInTheDocument());

  await userEvent.click(screen.getByTestId("nav-admin"));

  await waitFor(() =>
    expect(screen.getByRole("region", { name: /admin/i })).toBeInTheDocument(),
  );
  expect(screen.getByText(/user management/i)).toBeInTheDocument();
});
