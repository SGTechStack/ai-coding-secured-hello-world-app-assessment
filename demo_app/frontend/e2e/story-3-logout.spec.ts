import type { Page } from "@playwright/test";
import { expect, test } from "./fixtures";
import {
  corsHeaders,
  JOHN,
  LOGOUT_API,
  ME_PATH,
  logInAsJohn,
  loginForm,
  logoutButton,
} from "./support";

const LOGOUT_FAILED = "Unable to log out. Please try again.";

/** Resolves with the next `GET /me` response. Start waiting before the action that triggers it. */
function nextMe(page: Page) {
  return page.waitForResponse((r) => new URL(r.url()).pathname === ME_PATH);
}

/** Logs out through the navbar and waits for the login form. */
async function logOut(page: Page) {
  await logoutButton(page).click();
  await expect(page).toHaveURL("/login");
  await expect(loginForm(page).username).toBeVisible();
}

test.describe("Story 3: Logout", () => {
  test("Scenario 1: Authenticated user sees the Log out button in the navbar", async ({
    page,
  }) => {
    await logInAsJohn(page);

    await expect(page).toHaveURL("/");
    await expect(
      page.getByRole("banner").getByRole("button", { name: "Log out" }),
    ).toBeVisible();
  });

  test("Scenario 2: Anonymous visitor does not see the Log out button", async ({
    page,
  }) => {
    await page.goto("/login");

    await expect(loginForm(page).username).toBeVisible();
    await expect(logoutButton(page)).toHaveCount(0);
  });

  test("Scenario 3: Logging out ends the session and returns to login", async ({
    page,
  }) => {
    await logInAsJohn(page);

    await logOut(page);

    await expect(page.getByRole("heading", { name: "Log in" })).toBeVisible();
    await expect(page.getByText("Hello, John!")).toHaveCount(0);
    await expect(logoutButton(page)).toHaveCount(0);
  });

  test("Scenario 4: Logged-out session does not survive Back or reload", async ({
    page,
  }) => {
    await logInAsJohn(page);
    await logOut(page);

    await test.step("Back", async () => {
      const me = nextMe(page);
      await page.goBack();

      expect((await me).status()).toBe(401);
      await expect(page).toHaveURL("/login");
      await expect(loginForm(page).username).toBeVisible();
      await expect(page.getByText("Hello, John!")).toHaveCount(0);
    });

    await test.step("opening / directly", async () => {
      const me = nextMe(page);
      await page.goto("/");

      expect((await me).status()).toBe(401);
      await expect(page).toHaveURL("/login");
      await expect(loginForm(page).username).toBeVisible();
      await expect(page.getByText("Hello, John!")).toHaveCount(0);
    });
  });

  test("Scenario 5: User can log in again after logging out", async ({
    page,
  }) => {
    await logInAsJohn(page);
    await logOut(page);

    // Same page, no reload: nothing cached from the old session may get in the way.
    const form = loginForm(page);
    await form.username.fill(JOHN.username);
    await form.password.fill(JOHN.password);
    await form.submit.click();

    await expect(page).toHaveURL("/");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(
      "Hello, John!",
    );
    await expect(logoutButton(page)).toHaveText("Log out");
  });

  test("Scenario 6: Logout failure keeps the user signed in and explains why", async ({
    page,
  }) => {
    await logInAsJohn(page);
    const alert = page.getByRole("alert");

    await test.step("500 Internal Server Error", async () => {
      await page.route(LOGOUT_API, (route) =>
        route.fulfill({
          status: 500,
          headers: corsHeaders(route),
          contentType: "application/json",
          body: JSON.stringify({ message: "Internal Server Error" }),
        }),
      );
      await logoutButton(page).click();

      await expect(alert).toHaveText(LOGOUT_FAILED);
      await expect(logoutButton(page)).toBeEnabled();
      await expect(logoutButton(page)).toHaveText("Log out");
      await page.unrouteAll();
    });

    await test.step("network outage", async () => {
      // Hold the request so the in-flight state is observable, then drop the connection.
      let release!: () => void;
      const held = new Promise<void>((resolve) => (release = resolve));
      await page.route(LOGOUT_API, async (route) => {
        await held;
        await route.abort("internetdisconnected");
      });
      await logoutButton(page).click();

      // A new attempt clears the old alert, so the next one can only come from this request.
      await expect(alert).toHaveCount(0);
      await expect(logoutButton(page)).toBeDisabled();
      await expect(logoutButton(page)).toHaveText("Logging out...");
      release();

      await expect(alert).toHaveText(LOGOUT_FAILED);
      await expect(logoutButton(page)).toBeEnabled();
      await expect(logoutButton(page)).toHaveText("Log out");
    });

    await expect(page).toHaveURL("/");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(
      "Hello, John!",
    );
  });
});
