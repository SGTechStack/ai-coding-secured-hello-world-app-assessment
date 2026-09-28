import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import App from "../App.jsx";

beforeEach(() => {
  // No ?token= in the URL for these tests.
  window.history.pushState({}, "", "/");
});

afterEach(() => vi.restoreAllMocks());

test("logged out: shows the login form after the session check returns 401", async () => {
  // fetchGreeting() -> 401 (unauthenticated)
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({ ok: false, status: 401, json: async () => ({}) }),
  );

  render(<App />);

  await waitFor(() =>
    expect(screen.getByRole("region", { name: /login/i })).toBeInTheDocument(),
  );
  expect(screen.getByRole("button", { name: /create an account/i })).toBeInTheDocument();
});

test("authenticated: shows the greeting and a logout control", async () => {
  // Both the App session probe and the Greeting component call /api/hello.
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ message: "Hello, alice" }),
    }),
  );

  render(<App />);

  await waitFor(() =>
    expect(screen.getByTestId("greeting")).toHaveTextContent("Hello, alice"),
  );
  expect(screen.getByRole("button", { name: /log ?out/i })).toBeInTheDocument();
});
