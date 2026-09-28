import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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

/**
 * Fetch stub that routes by URL + method so both the initial user-list load and
 * the subsequent PATCH toggle resolve correctly. `onPatch` lets each test decide
 * how the toggle request responds.
 */
function routedFetch(listUsers, onPatch) {
  return vi.fn((url, options = {}) => {
    const method = options.method ?? "GET";
    if (url.endsWith("/api/admin/users") && method === "GET") {
      return Promise.resolve({ ok: true, json: async () => listUsers });
    }
    if (url.includes("/enabled") && method === "PATCH") {
      return onPatch(url, options);
    }
    // /api/ping (CSRF priming) and anything else.
    return Promise.resolve({ ok: true, json: async () => ({}) });
  });
}

test("toggle disables an enabled account and reflects the new state", async () => {
  const users = [
    {
      id: "11111111-1111-1111-1111-111111111111",
      username: "bob",
      email: "bob@example.com",
      role: "USER",
      enabled: true,
      createdAt: "2026-02-02T00:00:00Z",
    },
  ];
  const patch = vi.fn((url, options) => {
    const body = JSON.parse(options.body);
    return Promise.resolve({
      ok: true,
      json: async () => ({ ...users[0], enabled: body.enabled }),
    });
  });
  vi.stubGlobal("fetch", routedFetch(users, patch));

  render(<AdminUserTable />);

  const toggle = await screen.findByTestId("admin-user-toggle");
  expect(toggle).toHaveTextContent("Disable");

  await userEvent.click(toggle);

  // The PATCH was sent with enabled:false to the target's /enabled endpoint.
  await waitFor(() => expect(patch).toHaveBeenCalledTimes(1));
  const [patchUrl, patchOptions] = patch.mock.calls[0];
  expect(patchUrl).toContain(
    "/api/admin/users/11111111-1111-1111-1111-111111111111/enabled",
  );
  expect(patchOptions.method).toBe("PATCH");
  expect(JSON.parse(patchOptions.body)).toEqual({ enabled: false });

  // The row now reflects the disabled state.
  await waitFor(() => {
    const row = within(screen.getByTestId("admin-user-row"));
    expect(row.getByText("No")).toBeInTheDocument();
    expect(row.getByTestId("admin-user-toggle")).toHaveTextContent("Enable");
  });
});

test("surfaces the self-action error and leaves the row unchanged", async () => {
  const users = [
    {
      id: "22222222-2222-2222-2222-222222222222",
      username: "admin",
      email: "admin@example.com",
      role: "ADMIN",
      enabled: true,
      createdAt: "2026-01-01T00:00:00Z",
    },
  ];
  const patch = vi.fn(() =>
    Promise.resolve({
      ok: false,
      status: 400,
      json: async () => ({
        error: "self_action_forbidden",
        message: "an admin cannot change their own account status",
      }),
    }),
  );
  vi.stubGlobal("fetch", routedFetch(users, patch));

  render(<AdminUserTable />);

  const toggle = await screen.findByTestId("admin-user-toggle");
  await userEvent.click(toggle);

  await waitFor(() =>
    expect(screen.getByTestId("admin-users-action-error")).toHaveTextContent(
      /admin cannot change their own account status/i,
    ),
  );

  // The row is still enabled — the failed toggle did not flip local state.
  const row = within(screen.getByTestId("admin-user-row"));
  expect(row.getByText("Yes")).toBeInTheDocument();
  expect(row.getByTestId("admin-user-toggle")).toHaveTextContent("Disable");
});
