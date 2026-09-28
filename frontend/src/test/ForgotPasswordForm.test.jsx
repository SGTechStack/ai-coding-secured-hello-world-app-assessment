import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import ForgotPasswordForm from "../components/ForgotPasswordForm.jsx";

afterEach(() => vi.restoreAllMocks());

function mockFetchSequence(...responses) {
  const fn = vi.fn();
  responses.forEach((r) => fn.mockResolvedValueOnce(r));
  vi.stubGlobal("fetch", fn);
  return fn;
}

test("submits an email and shows the generic confirmation", async () => {
  mockFetchSequence(
    { ok: true, json: async () => ({ status: "ok" }) }, // ping (csrf prime)
    {
      ok: true,
      json: async () => ({
        message: "If an account exists for that email, a reset link has been sent.",
      }),
    },
  );

  render(<ForgotPasswordForm />);
  await userEvent.type(screen.getByRole("textbox", { name: /email/i }), "alice@example.com");
  await userEvent.click(screen.getByRole("button", { name: /send reset link/i }));

  await waitFor(() =>
    expect(screen.getByTestId("forgot-password-success")).toHaveTextContent(
      /if an account exists/i,
    ),
  );
});
