import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import RegisterForm from "../components/RegisterForm.jsx";

afterEach(() => vi.restoreAllMocks());

function mockFetchSequence(...responses) {
  const fn = vi.fn();
  responses.forEach((r) => fn.mockResolvedValueOnce(r));
  vi.stubGlobal("fetch", fn);
  return fn;
}

test("submits and shows success", async () => {
  mockFetchSequence(
    { ok: true, json: async () => ({ status: "ok" }) }, // ping (csrf prime)
    { ok: true, json: async () => ({ username: "alice", role: "USER" }) },
  );

  render(<RegisterForm />);
  await userEvent.type(screen.getByRole("textbox", { name: /username/i }), "alice");
  await userEvent.type(screen.getByRole("textbox", { name: /email/i }), "alice@example.com");
  await userEvent.type(document.querySelector('input[name="password"]'), "correcthorsebattery");
  await userEvent.click(screen.getByRole("button", { name: /register/i }));

  await waitFor(() =>
    expect(screen.getByTestId("register-success")).toHaveTextContent("alice"),
  );
});

test("shows server validation error", async () => {
  mockFetchSequence(
    { ok: true, json: async () => ({ status: "ok" }) },
    { ok: false, status: 409, json: async () => ({ message: "Username already registered" }) },
  );

  render(<RegisterForm />);
  await userEvent.type(screen.getByRole("textbox", { name: /username/i }), "bob");
  await userEvent.type(screen.getByRole("textbox", { name: /email/i }), "bob@example.com");
  await userEvent.type(document.querySelector('input[name="password"]'), "correcthorsebattery");
  await userEvent.click(screen.getByRole("button", { name: /register/i }));

  await waitFor(() =>
    expect(screen.getByTestId("register-error")).toHaveTextContent("already registered"),
  );
});
