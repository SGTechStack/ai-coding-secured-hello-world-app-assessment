import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import App from "../App.jsx";

afterEach(() => {
  vi.restoreAllMocks();
});

test("shows backend ok status when ping succeeds", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ status: "ok" }),
    }),
  );

  render(<App />);

  await waitFor(() =>
    expect(screen.getByTestId("backend-status")).toHaveTextContent("backend: ok"),
  );
});
