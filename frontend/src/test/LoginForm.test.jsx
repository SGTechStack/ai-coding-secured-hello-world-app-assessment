import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import LoginForm from "../components/LoginForm.jsx";

afterEach(() => vi.restoreAllMocks());

function mockFetchSequence(...responses) {
  const fn = vi.fn();
  responses.forEach((r) => fn.mockResolvedValueOnce(r));
  vi.stubGlobal("fetch", fn);
  return fn;
}

test("calls onLoggedIn on success", async () => {
  const onLoggedIn = vi.fn();
  mockFetchSequence(
    { ok: true, json: async () => ({ status: "ok" }) },
    { ok: true, json: async () => ({ username: "alice", role: "USER" }) },
  );

  render(<LoginForm onLoggedIn={onLoggedIn} />);
  await userEvent.type(screen.getByRole("textbox", { name: /username/i }), "alice");
  await userEvent.type(document.querySelector('input[name="password"]'), "correcthorsebattery");
  await userEvent.click(screen.getByRole("button", { name: /log in/i }));

  await waitFor(() => expect(onLoggedIn).toHaveBeenCalledWith({ username: "alice", role: "USER" }));
});

test("shows a generic error on failure", async () => {
  mockFetchSequence(
    { ok: true, json: async () => ({ status: "ok" }) },
    { ok: false, status: 401, json: async () => ({ message: "Invalid username or password" }) },
  );

  render(<LoginForm />);
  await userEvent.type(screen.getByRole("textbox", { name: /username/i }), "alice");
  await userEvent.type(document.querySelector('input[name="password"]'), "wrongpassword!");
  await userEvent.click(screen.getByRole("button", { name: /log in/i }));

  await waitFor(() =>
    expect(screen.getByTestId("login-error")).toHaveTextContent("Invalid username or password"),
  );
});
