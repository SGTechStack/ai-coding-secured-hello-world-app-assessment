import { screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import type { AdminUser } from "../api/admin";
import {
  adminProfile,
  adminUsers,
  api,
  csrfToken,
  demoUser,
} from "../test/handlers";
import { renderApp } from "../test/renderApp";
import { server } from "../test/server";

function signedInAs(profile: typeof demoUser) {
  server.use(
    http.get(api("/api/v1/auth/me"), () => HttpResponse.json(profile)),
  );
}

function listReturns(users: AdminUser[]) {
  server.use(
    http.get(api("/api/v1/admin/users"), () => HttpResponse.json(users)),
  );
}

/** The table row showing `username`. */
function rowOf(username: string) {
  const row = screen
    .getAllByRole("row")
    .find((candidate) =>
      within(candidate).queryByRole("rowheader", { name: username }),
    );
  if (!row) throw new Error(`no row for ${username}`);
  return row;
}

type Sent = {
  method: string;
  path: string;
  body: unknown;
  csrf: string | null;
};

/**
 * A stateful admin API: the list reflects every successful change, and each change request is
 * recorded. `listRequests` counts list fetches, so a test can see the refresh.
 */
function fakeAdminApi() {
  let users = adminUsers.map((user) => ({ ...user }));
  const sent: Sent[] = [];
  const counts = { listRequests: 0 };
  const record = async (request: Request) => {
    const text = await request.text();
    sent.push({
      method: request.method,
      path: new URL(request.url).pathname,
      body: text ? JSON.parse(text) : undefined,
      csrf: request.headers.get("X-XSRF-TOKEN"),
    });
    return text ? (JSON.parse(text) as Record<string, unknown>) : {};
  };
  const update = (
    id: string | readonly string[] | undefined,
    change: object,
  ) => {
    users = users.map((user) =>
      String(user.id) === id ? { ...user, ...change } : user,
    );
    return HttpResponse.json(users.find((user) => String(user.id) === id));
  };
  server.use(
    http.get(api("/api/v1/admin/users"), () => {
      counts.listRequests += 1;
      return HttpResponse.json(users);
    }),
    http.patch(
      api("/api/v1/admin/users/:id/status"),
      async ({ request, params }) =>
        update(params.id, { enabled: (await record(request)).enabled }),
    ),
    http.patch(
      api("/api/v1/admin/users/:id/role"),
      async ({ request, params }) =>
        update(params.id, { role: (await record(request)).role }),
    ),
    http.delete(api("/api/v1/admin/users/:id"), async ({ request, params }) => {
      await record(request);
      users = users.filter((user) => String(user.id) !== params.id);
      return new HttpResponse(null, { status: 204 });
    }),
  );
  return { sent, counts };
}

/** Opens the admin page as the admin and waits for the table. */
async function openAsAdmin() {
  signedInAs(adminProfile);
  const app = renderApp("/admin/users");
  await screen.findByRole("table", { name: "Users" });
  return app;
}

describe("/admin/users (admin user list)", () => {
  it("shows the admin a table of every user with its caption and column headers", async () => {
    signedInAs(adminProfile);
    renderApp("/admin/users");

    const table = await screen.findByRole("table", { name: "Users" });
    const headers = within(table)
      .getAllByRole("columnheader")
      .map((header) => header.textContent);
    expect(headers).toEqual([
      "Username",
      "Email",
      "First name",
      "Role",
      "Status",
      "Created",
      "Actions",
    ]);

    const john = rowOf("johndoe");
    expect(within(john).getByText("johndoe@example.com")).toBeInTheDocument();
    expect(within(john).getByText("John")).toBeInTheDocument();
    expect(within(john).getByText("USER")).toBeInTheDocument();
    expect(within(john).getByText("Enabled")).toBeInTheDocument();
    const created = within(john).getByText(
      (_, element) => element?.tagName === "TIME",
    );
    expect(created).toHaveAttribute("dateTime", "2026-01-15T09:30:00Z");
    expect(created.textContent).not.toBe("");

    expect(within(rowOf("admin")).getByText("ADMIN")).toBeInTheDocument();
  });

  it("shows a disabled account as Disabled", async () => {
    signedInAs(adminProfile);
    listReturns([{ ...adminUsers[0]!, enabled: false }, adminUsers[1]!]);
    renderApp("/admin/users");

    await screen.findByRole("table", { name: "Users" });
    expect(within(rowOf("johndoe")).getByText("Disabled")).toBeInTheDocument();
  });

  it("disables the controls on the admin's own row and says why", async () => {
    signedInAs(adminProfile);
    renderApp("/admin/users");

    await screen.findByRole("table", { name: "Users" });
    const buttons = within(rowOf("admin")).getAllByRole("button");
    expect(buttons.map((button) => button.textContent)).toEqual([
      "Disable",
      "Make user",
      "Delete",
    ]);
    for (const button of buttons) {
      expect(button).toBeDisabled();
      expect(button).toHaveAccessibleDescription(
        "You can't change your own account.",
      );
    }
    expect(
      within(rowOf("admin")).getByText("You can't change your own account."),
    ).toBeInTheDocument();
  });

  it("shows an HTML-looking first name as literal text, never as markup", async () => {
    const firstName = "<img src=x onerror=alert(1)>";
    signedInAs(adminProfile);
    listReturns([{ ...adminUsers[0]!, firstName }, adminUsers[1]!]);
    renderApp("/admin/users");

    await screen.findByRole("table", { name: "Users" });
    expect(within(rowOf("johndoe")).getByText(firstName)).toBeInTheDocument();
    expect(document.querySelector("img")).toBeNull();
  });

  it("shows an error alert when the list fails to load", async () => {
    signedInAs(adminProfile);
    server.use(
      http.get(api("/api/v1/admin/users"), () =>
        HttpResponse.json({ message: "Something went wrong" }, { status: 500 }),
      ),
    );
    renderApp("/admin/users");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Unable to load users. Please try again later.",
    );
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("shows an error alert when the server can't be reached", async () => {
    signedInAs(adminProfile);
    server.use(
      http.get(api("/api/v1/admin/users"), () => HttpResponse.error()),
    );
    renderApp("/admin/users");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Unable to load users. Please try again later.",
    );
  });

  describe("row actions", () => {
    it("disables and re-enables a user, then refreshes the list", async () => {
      const { sent, counts } = fakeAdminApi();
      const { user } = await openAsAdmin();

      await user.click(
        within(rowOf("johndoe")).getByRole("button", { name: "Disable" }),
      );
      expect(
        await within(rowOf("johndoe")).findByText("Disabled"),
      ).toBeInTheDocument();
      expect(sent).toEqual([
        {
          method: "PATCH",
          path: "/api/v1/admin/users/1/status",
          body: { enabled: false },
          csrf: csrfToken,
        },
      ]);
      expect(counts.listRequests).toBe(2);

      await user.click(
        within(rowOf("johndoe")).getByRole("button", { name: "Enable" }),
      );
      expect(
        await within(rowOf("johndoe")).findByText("Enabled"),
      ).toBeInTheDocument();
      expect(sent[1]?.body).toEqual({ enabled: true });
    });

    it("promotes a user and demotes them again, refreshing the list", async () => {
      const { sent } = fakeAdminApi();
      const { user } = await openAsAdmin();

      await user.click(
        within(rowOf("johndoe")).getByRole("button", { name: "Make admin" }),
      );
      expect(
        await within(rowOf("johndoe")).findByRole("button", {
          name: "Make user",
        }),
      ).toBeInTheDocument();
      expect(within(rowOf("johndoe")).getByText("ADMIN")).toBeInTheDocument();
      expect(sent[0]).toMatchObject({
        method: "PATCH",
        path: "/api/v1/admin/users/1/role",
        body: { role: "ADMIN" },
      });

      await user.click(
        within(rowOf("johndoe")).getByRole("button", { name: "Make user" }),
      );
      expect(
        await within(rowOf("johndoe")).findByText("USER"),
      ).toBeInTheDocument();
      expect(sent[1]?.body).toEqual({ role: "USER" });
    });

    it("deletes only after confirming, then the row disappears", async () => {
      const { sent } = fakeAdminApi();
      const { user } = await openAsAdmin();

      await user.click(
        within(rowOf("johndoe")).getByRole("button", { name: "Delete" }),
      );
      const dialog = screen.getByRole("alertdialog", {
        name: "Delete johndoe?",
      });
      expect(dialog).toHaveAccessibleDescription(
        "This permanently deletes the account and signs it out everywhere. It can't be undone.",
      );
      expect(sent).toEqual([]);

      await user.click(within(dialog).getByRole("button", { name: "Delete" }));
      await waitFor(() =>
        expect(
          screen.queryByRole("rowheader", { name: "johndoe" }),
        ).not.toBeInTheDocument(),
      );
      expect(sent).toEqual([
        {
          method: "DELETE",
          path: "/api/v1/admin/users/1",
          body: undefined,
          csrf: csrfToken,
        },
      ]);
      expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    });

    it("retries an action once with a fresh CSRF token after a 403", async () => {
      const { sent } = fakeAdminApi();
      let attempts = 0;
      let primes = 0;
      server.use(
        http.get(api("/api/v1/auth/csrf"), () => {
          primes += 1;
          return HttpResponse.json({
            headerName: "X-XSRF-TOKEN",
            token: `token-${primes}`,
          });
        }),
        http.patch(api("/api/v1/admin/users/:id/status"), ({ request }) => {
          attempts += 1;
          if (attempts === 1)
            return HttpResponse.json({ message: "Forbidden" }, { status: 403 });
          sent.push({
            method: request.method,
            path: new URL(request.url).pathname,
            body: undefined,
            csrf: request.headers.get("X-XSRF-TOKEN"),
          });
          return HttpResponse.json({ ...adminUsers[0]!, enabled: false });
        }),
      );
      const { user } = await openAsAdmin();

      await user.click(
        within(rowOf("johndoe")).getByRole("button", { name: "Disable" }),
      );

      await waitFor(() => expect(sent).toHaveLength(1));
      expect(attempts).toBe(2);
      expect(primes).toBe(2);
      expect(sent[0]?.csrf).toBe("token-2");
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("sends nothing when the delete is cancelled", async () => {
      const { sent } = fakeAdminApi();
      const { user } = await openAsAdmin();

      await user.click(
        within(rowOf("johndoe")).getByRole("button", { name: "Delete" }),
      );
      await user.click(
        within(screen.getByRole("alertdialog")).getByRole("button", {
          name: "Cancel",
        }),
      );

      expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
      expect(rowOf("johndoe")).toBeInTheDocument();
      expect(sent).toEqual([]);
    });

    it("shows an error alert when an action fails, and clears it on the next one", async () => {
      fakeAdminApi();
      server.use(
        http.patch(api("/api/v1/admin/users/:id/status"), () =>
          HttpResponse.json(
            { message: "Something went wrong" },
            { status: 500 },
          ),
        ),
      );
      const { user } = await openAsAdmin();

      await user.click(
        within(rowOf("johndoe")).getByRole("button", { name: "Disable" }),
      );
      expect(await screen.findByRole("alert")).toHaveTextContent(
        "Unable to change johndoe. Please try again later.",
      );
      expect(within(rowOf("johndoe")).getByText("Enabled")).toBeInTheDocument();

      await user.click(
        within(rowOf("johndoe")).getByRole("button", { name: "Make admin" }),
      );
      await waitFor(() =>
        expect(screen.queryByRole("alert")).not.toBeInTheDocument(),
      );
    });

    it("says so when the user no longer exists", async () => {
      fakeAdminApi();
      server.use(
        http.delete(api("/api/v1/admin/users/:id"), () =>
          HttpResponse.json(
            { code: "USER_NOT_FOUND", message: "User not found" },
            { status: 404 },
          ),
        ),
      );
      const { user } = await openAsAdmin();

      await user.click(
        within(rowOf("johndoe")).getByRole("button", { name: "Delete" }),
      );
      await user.click(
        within(screen.getByRole("alertdialog")).getByRole("button", {
          name: "Delete",
        }),
      );

      expect(await screen.findByRole("alert")).toHaveTextContent(
        "johndoe no longer exists.",
      );
      expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    });

    it("shows the server's refusal of a self-action", async () => {
      fakeAdminApi();
      server.use(
        http.patch(api("/api/v1/admin/users/:id/role"), () =>
          HttpResponse.json(
            { code: "SELF_ACTION_NOT_ALLOWED", message: "No" },
            { status: 409 },
          ),
        ),
      );
      const { user } = await openAsAdmin();

      await user.click(
        within(rowOf("johndoe")).getByRole("button", { name: "Make admin" }),
      );
      expect(await screen.findByRole("alert")).toHaveTextContent(
        "You can't change your own account.",
      );
    });

    it("enables every control on other users' rows", async () => {
      await openAsAdmin();
      const buttons = within(rowOf("johndoe")).getAllByRole("button");
      expect(buttons.map((button) => button.textContent)).toEqual([
        "Disable",
        "Make admin",
        "Delete",
      ]);
      for (const button of buttons) {
        expect(button).toBeEnabled();
        expect(button).not.toHaveAccessibleDescription();
      }
    });
  });

  describe("a session the server has ended", () => {
    it("sends the admin to /login when the list answers 401", async () => {
      signedInAs(adminProfile);
      server.use(
        http.get(api("/api/v1/admin/users"), () =>
          HttpResponse.json({ message: "Unauthorized" }, { status: 401 }),
        ),
      );
      const { router } = renderApp("/admin/users");
      // The session is gone: /me answers 401 from now on.
      server.use(
        http.get(api("/api/v1/auth/me"), () =>
          HttpResponse.json({ message: "Unauthorized" }, { status: 401 }),
        ),
      );

      expect(await screen.findByLabelText("Username")).toBeInTheDocument();
      expect(router.state.location.pathname).toBe("/login");
      await waitFor(() =>
        expect(
          screen.queryByRole("button", { name: /^Log out/ }),
        ).not.toBeInTheDocument(),
      );
    });

    it("sends the admin to /login when an action answers 401", async () => {
      fakeAdminApi();
      const { user, router } = await openAsAdmin();
      server.use(
        http.patch(api("/api/v1/admin/users/:id/role"), () =>
          HttpResponse.json({ message: "Unauthorized" }, { status: 401 }),
        ),
        http.get(api("/api/v1/auth/me"), () =>
          HttpResponse.json({ message: "Unauthorized" }, { status: 401 }),
        ),
      );

      await user.click(
        within(rowOf("johndoe")).getByRole("button", { name: "Make admin" }),
      );

      expect(await screen.findByLabelText("Username")).toBeInTheDocument();
      expect(router.state.location.pathname).toBe("/login");
      expect(screen.queryByRole("table")).not.toBeInTheDocument();
      await waitFor(() =>
        expect(
          screen.queryByRole("button", { name: /^Log out/ }),
        ).not.toBeInTheDocument(),
      );
    });
  });

  describe("guard", () => {
    it("sends a non-admin to / without asking for the list", async () => {
      let listRequests = 0;
      signedInAs(demoUser);
      server.use(
        http.get(api("/api/v1/admin/users"), () => {
          listRequests += 1;
          return HttpResponse.json([], { status: 403 });
        }),
      );
      const { router } = renderApp("/admin/users");

      expect(
        await screen.findByRole("heading", { name: "Hello, John!" }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe("/");
      expect(screen.queryByRole("table")).not.toBeInTheDocument();
      expect(listRequests).toBe(0);
    });

    it("sends a visitor without a session to /login", async () => {
      const { router } = renderApp("/admin/users");

      expect(await screen.findByLabelText("Username")).toBeInTheDocument();
      await waitFor(() =>
        expect(router.state.location.pathname).toBe("/login"),
      );
    });
  });
});
