import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import LogoutButton from "../components/LogoutButton.jsx";

afterEach(() => vi.restoreAllMocks());

test("ends the session and returns to the unauthenticated view", async () => {
  const onLoggedOut = vi.fn();
  // The logout POST resolves 204 (no content, ok:true).
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({ ok: true, status: 204, json: async () => ({}) }),
  );

  render(<LogoutButton onLoggedOut={onLoggedOut} />);
  await userEvent.click(screen.getByRole("button", { name: /log out/i }));

  await waitFor(() => expect(onLoggedOut).toHaveBeenCalledTimes(1));
  expect(screen.queryByTestId("logout-error")).toBeNull();
});

test("does not leave the session view on failure", async () => {
  const onLoggedOut = vi.fn();
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({ ok: false, status: 401, json: async () => ({}) }),
  );

  render(<LogoutButton onLoggedOut={onLoggedOut} />);
  await userEvent.click(screen.getByRole("button", { name: /log out/i }));

  await waitFor(() =>
    expect(screen.getByTestId("logout-error")).toHaveTextContent("logout failed: 401"),
  );
  expect(onLoggedOut).not.toHaveBeenCalled();
});
