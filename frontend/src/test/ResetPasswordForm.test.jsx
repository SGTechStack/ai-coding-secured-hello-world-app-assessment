import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import ResetPasswordForm from "../components/ResetPasswordForm.jsx";

afterEach(() => vi.restoreAllMocks());

function mockFetchSequence(...responses) {
  const fn = vi.fn();
  responses.forEach((r) => fn.mockResolvedValueOnce(r));
  vi.stubGlobal("fetch", fn);
  return fn;
}

test("submits the token + new password and shows the success confirmation", async () => {
  mockFetchSequence(
    { ok: true, json: async () => ({ status: "ok" }) }, // ping (csrf prime)
    { ok: true, json: async () => ({ message: "Your password has been reset." }) },
  );

  render(<ResetPasswordForm token="reset-token-123" />);
  await userEvent.type(
    screen.getByLabelText(/new password/i),
    "brandnewpassword123",
  );
  await userEvent.click(screen.getByRole("button", { name: /set new password/i }));

  await waitFor(() =>
    expect(screen.getByTestId("reset-password-success")).toHaveTextContent(
      /your password has been reset/i,
    ),
  );
});

test("shows the backend error when the token is invalid or expired", async () => {
  mockFetchSequence(
    { ok: true, json: async () => ({ status: "ok" }) }, // ping (csrf prime)
    {
      ok: false,
      status: 400,
      json: async () => ({ message: "invalid or expired reset token" }),
    },
  );

  render(<ResetPasswordForm token="bad-token" />);
  await userEvent.type(
    screen.getByLabelText(/new password/i),
    "brandnewpassword123",
  );
  await userEvent.click(screen.getByRole("button", { name: /set new password/i }));

  await waitFor(() =>
    expect(screen.getByTestId("reset-password-error")).toHaveTextContent(
      /invalid or expired reset token/i,
    ),
  );
});

test("rejects a too-short password client-side without calling the API", async () => {
  const fetchMock = mockFetchSequence();

  render(<ResetPasswordForm token="reset-token-123" />);
  await userEvent.type(screen.getByLabelText(/new password/i), "short");
  await userEvent.click(screen.getByRole("button", { name: /set new password/i }));

  expect(await screen.findByTestId("reset-password-error")).toHaveTextContent(
    /at least 12 characters/i,
  );
  // No network call was made — validation short-circuited before fetch.
  expect(fetchMock).not.toHaveBeenCalled();
});
