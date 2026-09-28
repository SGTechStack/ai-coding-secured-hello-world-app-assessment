import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import Greeting from "../components/Greeting.jsx";

afterEach(() => vi.restoreAllMocks());

test("shows the greeting when authenticated", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ message: "Hello, alice" }),
    }),
  );

  render(<Greeting />);
  await waitFor(() =>
    expect(screen.getByTestId("greeting")).toHaveTextContent("Hello, alice"),
  );
});

test("shows a gated message when unauthenticated (401)", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({ ok: false, status: 401, json: async () => ({}) }),
  );

  render(<Greeting />);
  await waitFor(() =>
    expect(screen.getByTestId("greeting-gated")).toBeInTheDocument(),
  );
});
