import { expect, test, type Page, type Response } from "@playwright/test";
import {
  corsHeaders,
  INVALID_CREDENTIALS,
  JOHN,
  LOGIN_API,
  UNAVAILABLE,
  expectBannerAboveForm,
  expectFormEnabled,
  loginForm,
} from "./support";

/** Every request the page sends to the login endpoint, in order. */
function recordLoginRequests(page: Page): string[] {
  const sent: string[] = [];
  page.on("request", (request) => {
    if (new URL(request.url()).pathname === "/api/v1/auth/login") {
      sent.push(request.url());
    }
  });
  return sent;
}

/** Submits the given credentials; resolves with the real login response. */
async function failLogin(page: Page, username: string, password: string) {
  const form = loginForm(page);
  await form.username.fill(username);
  await form.password.fill(password);
  const response = page.waitForResponse(
    (r) => new URL(r.url()).pathname === "/api/v1/auth/login",
  );
  await form.submit.click();
  return response;
}

/** Checks a failed login's error body; returns it without the timestamp, for comparison. */
async function invalidCredentialsBody(response: Response) {
  const { timestamp, ...rest } = await response.json();
  expect(rest).toEqual({
    status: 401,
    code: "INVALID_CREDENTIALS",
    message: INVALID_CREDENTIALS,
    path: "/api/v1/auth/login",
  });
  expect(typeof timestamp).toBe("string");
  return rest;
}

test.describe("Story 1: User Authentication & Login Flow", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/login");
    await expect(page.getByRole("heading", { name: "Log in" })).toBeVisible();
  });

  test("Scenario 1: Successful login and redirects to landing page", async ({
    page,
  }) => {
    const form = loginForm(page);
    await form.username.fill(JOHN.username);
    await form.password.fill(JOHN.password);
    await form.submit.click();

    await expect(page).toHaveURL("/");
    await expect(
      page.getByRole("heading", { name: "Hello, John!" }),
    ).toBeVisible();
  });

  test("Scenario 2: Prevent submission when username or password is empty", async ({
    page,
  }) => {
    const form = loginForm(page);
    const loginRequests = recordLoginRequests(page);
    const usernameError = page.getByText("Username is required");
    const passwordError = page.getByText("Password is required");

    await test.step("both fields blank", async () => {
      await form.submit.click();
      await expect(usernameError).toBeVisible();
      await expect(passwordError).toBeVisible();
      await expect(form.username).toHaveAttribute("aria-invalid", "true");
      await expect(form.password).toHaveAttribute("aria-invalid", "true");
    });

    await test.step("only the password blank", async () => {
      await page.reload();
      await form.username.fill(JOHN.username);
      await form.submit.click();
      await expect(passwordError).toBeVisible();
      await expect(usernameError).toBeHidden();
    });

    await test.step("only the username blank", async () => {
      await page.reload();
      await form.password.fill(JOHN.password);
      await form.submit.click();
      await expect(usernameError).toBeVisible();
      await expect(passwordError).toBeHidden();
    });

    // Proving a negative without a fixed wait: a complete submit now sends a request. Requests are
    // recorded in the order they are sent, so a stray one from any blocked submit above would
    // already be in the list ahead of it.
    await test.step("both fields filled: the first login request goes out", async () => {
      await form.username.fill(JOHN.username);
      const sent = page.waitForRequest(
        (r) => new URL(r.url()).pathname === "/api/v1/auth/login",
      );
      await form.submit.click();
      await sent;
      expect(loginRequests).toHaveLength(1);
    });
  });

  test("Scenario 3: Validation errors do not appear while the user is actively typing prior to submit", async ({
    page,
  }) => {
    const form = loginForm(page);
    await form.username.pressSequentially("<>!@#$%^&*() ");
    // Leaving the field must not validate either: validation waits for submit.
    await form.password.focus();

    await expect(page.getByText("Username is required")).toBeHidden();
    await expect(page.getByText("Password is required")).toBeHidden();
    await expect(form.banner).toHaveCount(0);
    await expect(form.username).not.toHaveAttribute("aria-invalid");
    await expect(form.password).not.toHaveAttribute("aria-invalid");
  });

  test("Scenario 4: Inline error disappears immediately when the corresponding input changes", async ({
    page,
  }) => {
    const form = loginForm(page);
    const usernameError = page.getByText("Username is required");
    await form.submit.click();
    await expect(usernameError).toBeVisible();

    await form.username.press("j");

    await expect(usernameError).toBeHidden();
    await expect(form.username).not.toHaveAttribute("aria-invalid");
    // Only the edited field's error is dismissed.
    await expect(page.getByText("Password is required")).toBeVisible();
  });

  test("Scenario 5: Form elements disabled and loading feedback shown for at least 400ms", async ({
    page,
  }) => {
    const form = loginForm(page);
    await form.username.fill(JOHN.username);
    await form.password.fill(JOHN.password);

    // Time the loading state in the page itself: from the moment the button is disabled until it
    // is enabled again or leaves the DOM (navigation to "/"). DOM mutations are observed exactly,
    // with no polling interval to blur the measurement.
    await page.evaluate(() => {
      const w = window as Window & { loadingMs?: Promise<number> };
      w.loadingMs = new Promise((resolve) => {
        let start: number | undefined;
        const observer = new MutationObserver(() => {
          const button = document.querySelector<HTMLButtonElement>(
            'form button[type="submit"]',
          );
          const busy = button?.disabled === true;
          if (busy && start === undefined) start = performance.now();
          if (!busy && start !== undefined) {
            observer.disconnect();
            resolve(performance.now() - start);
          }
        });
        observer.observe(document.body, {
          subtree: true,
          childList: true,
          attributes: true,
          attributeFilter: ["disabled"],
        });
      });
    });
    const loginResponse = page.waitForResponse(
      (r) => new URL(r.url()).pathname === "/api/v1/auth/login",
    );

    await form.submit.click();

    await expect(form.username).toBeDisabled();
    await expect(form.password).toBeDisabled();
    await expect(form.submit).toBeDisabled();
    await expect(form.submit).toHaveText("Logging in...");
    const dotAnimation = await form.submit
      .locator(".ellipsis span")
      .first()
      .evaluate((dot) => getComputedStyle(dot).animationName);
    expect(dotAnimation).not.toBe("none");

    expect((await loginResponse).status()).toBe(200);
    await expect(page).toHaveURL("/");
    const loadingMs = await page.evaluate(
      () => (window as Window & { loadingMs?: Promise<number> }).loadingMs,
    );
    expect(loadingMs).toBeGreaterThanOrEqual(400);
  });

  test("Scenario 6: Display generic, non-leaking failure banner on incorrect credentials", async ({
    page,
  }) => {
    const form = loginForm(page);

    // The two bodies may differ only in their timestamp.
    const bodies: unknown[] = [];

    await test.step("wrong password for an existing user", async () => {
      const response = await failLogin(page, JOHN.username, "wrong-password");
      expect(response.status()).toBe(401);
      bodies.push(await invalidCredentialsBody(response));
      await expect(form.banner).toHaveText(INVALID_CREDENTIALS);
      await expectBannerAboveForm(page);
      await expectFormEnabled(page);
    });

    await test.step("unknown username", async () => {
      const response = await failLogin(page, "nobody-here", JOHN.password);
      expect(response.status()).toBe(401);
      bodies.push(await invalidCredentialsBody(response));
      await expect(form.banner).toHaveText(INVALID_CREDENTIALS);
      await expectBannerAboveForm(page);
      await expectFormEnabled(page);
    });

    expect(bodies[1]).toEqual(bodies[0]);
    // Nothing else on the page points at one field or the other.
    await expect(form.banner).toHaveCount(1);
    await expect(form.username).not.toHaveAttribute("aria-invalid");
    await expect(form.password).not.toHaveAttribute("aria-invalid");
  });

  test("Scenario 7: Authentication failure banner disappears when either field is edited", async ({
    page,
  }) => {
    const form = loginForm(page);

    await test.step("typing into the password field", async () => {
      await failLogin(page, JOHN.username, "wrong-password");
      await expect(form.banner).toHaveText(INVALID_CREDENTIALS);
      await form.password.press("x");
      await expect(form.banner).toHaveCount(0);
    });

    await test.step("typing into the username field", async () => {
      await failLogin(page, JOHN.username, "wrong-password");
      await expect(form.banner).toHaveText(INVALID_CREDENTIALS);
      await form.username.press("x");
      await expect(form.banner).toHaveCount(0);
    });
  });

  test("Scenario 8: Distinct error banner displayed for server 5xx or connection outages", async ({
    page,
  }) => {
    const form = loginForm(page);
    await form.username.fill(JOHN.username);
    await form.password.fill(JOHN.password);

    await test.step("500 Internal Server Error", async () => {
      await page.route(LOGIN_API, (route) =>
        route.fulfill({
          status: 500,
          headers: corsHeaders(route),
          contentType: "application/json",
          body: JSON.stringify({ message: "Internal Server Error" }),
        }),
      );
      await form.submit.click();
      await expect(form.banner).toHaveText(UNAVAILABLE);
      await expectBannerAboveForm(page);
      await expectFormEnabled(page);
      await page.unrouteAll();
    });

    // Clear the first banner so the next one can only come from the next request.
    await form.password.press("x");
    await expect(form.banner).toHaveCount(0);

    await test.step("network outage", async () => {
      await page.route(LOGIN_API, (route) =>
        route.abort("internetdisconnected"),
      );
      await form.submit.click();
      await expect(form.banner).toHaveText(UNAVAILABLE);
      await expectBannerAboveForm(page);
      await expectFormEnabled(page);
    });

    await expect(page).toHaveURL("/login");
  });
});
