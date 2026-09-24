import { expect, test } from "./fixtures";
import {
  INVALID_CREDENTIALS,
  LOGIN_API,
  loginForm,
  registerThroughUi,
  uniqueUser,
} from "./support";

test.describe("Story 5: Lockout", () => {
  test("Scenario 1: After 5 failed logins, the correct password shows the generic failure", async ({
    page,
  }, testInfo) => {
    // A fresh account, so no other test's failures or successes touch its counter.
    const user = uniqueUser(testInfo);
    await registerThroughUi(page, user);
    const form = loginForm(page);

    /** Submits the login form and waits for the API's reply. */
    async function submit(password: string) {
      await form.username.fill(user.username);
      await form.password.fill(password);
      const [response] = await Promise.all([
        page.waitForResponse(LOGIN_API),
        form.submit.click(),
      ]);
      return response;
    }

    await test.step("fail 5 times", async () => {
      for (let attempt = 1; attempt <= 5; attempt++) {
        const response = await submit(`wrong password ${attempt}`);
        expect(response.status()).toBe(401);
        await expect(form.banner).toHaveText(INVALID_CREDENTIALS);
      }
    });

    await test.step("the correct password is refused with the same message", async () => {
      const response = await submit(user.password);
      expect(response.status()).toBe(401);
      await expect(form.banner).toHaveText(INVALID_CREDENTIALS);
      await expect(page).toHaveURL("/login");
    });
  });

  // Scenario 2 (the correct password succeeds after the cooldown) needs time travel, so it is
  // covered at the API seam only: LockoutApiTest in the backend.
});
