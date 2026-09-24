import { screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import type { AdminUser } from "../api/admin";
import { adminProfile, adminUsers, api, demoUser } from "../test/handlers";
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
