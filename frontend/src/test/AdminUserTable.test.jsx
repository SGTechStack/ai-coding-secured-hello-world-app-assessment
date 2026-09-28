import { render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import AdminUserTable from "../components/AdminUserTable.jsx";

afterEach(() => vi.restoreAllMocks());

test("renders the admin user table from a mocked users response", async () => {
  const users = [
    {
      username: "alice",
      email: "alice@example.com",
      role: "ADMIN",
      enabled: true,
      createdAt: "2026-01-01T00:00:00Z",
    },
    {
      username: "bob",
      email: "bob@example.com",
      role: "USER",
      enabled: false,
      createdAt: "2026-02-02T00:00:00Z",
    },
  ];
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({ ok: true, json: async () => users }),
  );

  render(<AdminUserTable />);

  await waitFor(() =>
    expect(screen.getByTestId("admin-users-table")).toBeInTheDocument(),
  );

  const rows = screen.getAllByTestId("admin-user-row");
  expect(rows).toHaveLength(2);

  const alice = within(rows[0]);
  expect(alice.getByText("alice")).toBeInTheDocument();
  expect(alice.getByText("alice@example.com")).toBeInTheDocument();
  expect(alice.getByText("ADMIN")).toBeInTheDocument();
  expect(alice.getByText("Yes")).toBeInTheDocument();

  const bob = within(rows[1]);
  expect(bob.getByText("bob")).toBeInTheDocument();
  expect(bob.getByText("USER")).toBeInTheDocument();
  expect(bob.getByText("No")).toBeInTheDocument();

  // No password hash is ever rendered.
  expect(screen.queryByText(/passwordHash/i)).not.toBeInTheDocument();
});

test("surfaces an error when the fetch fails (e.g. a non-admin gets 403)", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({ ok: false, status: 403, json: async () => ({}) }),
  );

  render(<AdminUserTable />);

  await waitFor(() =>
    expect(screen.getByTestId("admin-users-error")).toHaveTextContent(/403/),
  );
});
