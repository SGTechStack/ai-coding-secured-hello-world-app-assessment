import { expect, test } from "./fixtures";
import { loginForm, logInThroughUi, JOHN } from "./support";

test.describe("Story 2: Session Persistence & Protected Route Access", () => {
  test("Scenario 1: Authenticated user is greeted by first name upon landing", async ({
    page,
  }) => {
    await logInThroughUi(page, JOHN);

    await expect(page).toHaveURL("/");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(
      "Hello, John!",
    );
  });

  test("Scenario 2: Authenticated session persists upon browser reload via session cookie", async ({
    page,
  }) => {
    await logInThroughUi(page, JOHN);

    // A reload drops all in-memory state, so the greeting can only come from the server session.
    const me = page.waitForResponse(
      (r) => new URL(r.url()).pathname === "/api/v1/auth/me",
    );
    await page.reload();

    expect((await me).status()).toBe(200);
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(
      "Hello, John!",
    );
    await expect(page).toHaveURL("/");
    await expect(loginForm(page).username).toHaveCount(0);
  });

  test("Scenario 3: Unauthenticated user navigating to landing page is redirected to login", async ({
    page,
  }) => {
    await page.goto("/");

    await expect(page).toHaveURL("/login");
    await expect(page.getByRole("heading", { name: "Log in" })).toBeVisible();
    await expect(page.getByText(/Hello/)).toHaveCount(0);
  });

  test("Scenario 4: Authenticated user navigating to login page is redirected to landing page", async ({
    page,
  }) => {
    await logInThroughUi(page, JOHN);

    await page.goto("/login");

    await expect(page).toHaveURL("/");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(
      "Hello, John!",
    );
    await expect(loginForm(page).username).toHaveCount(0);
  });
});
