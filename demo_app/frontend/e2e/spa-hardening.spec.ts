import { expect, test } from "./fixtures";

// The production build as `vite preview` serves it (spec: SPA hardening). The same API origin
// Playwright builds the SPA against (playwright.config.ts).
const API_ORIGIN = `http://localhost:${process.env.BACKEND_PORT ?? "8080"}`;

test.describe("SPA hardening", () => {
  test("the served document carries the CSP and the other security headers", async ({
    page,
  }) => {
    const response = await page.goto("/login");
    expect(response).not.toBeNull();
    const headers = response!.headers();
    expect(headers["content-security-policy"]).toBe(
      "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; " +
        `font-src 'self'; connect-src 'self' ${API_ORIGIN}; object-src 'none'; ` +
        "base-uri 'none'; form-action 'self'; frame-ancestors 'none'; " +
        "require-trusted-types-for 'script'",
    );
    expect(headers["referrer-policy"]).toBe("no-referrer");
    expect(headers["x-content-type-options"]).toBe("nosniff");
    expect(headers["permissions-policy"]).toBe(
      "camera=(), geolocation=(), microphone=()",
    );
    expect(headers["x-frame-options"]).toBe("DENY");
    await expect(page.locator('meta[name="referrer"]')).toHaveAttribute(
      "content",
      "no-referrer",
    );
  });

  test.describe("injected content", () => {
    // These tests cause violations on purpose and assert them instead of failing on them.
    test.use({ failOnCspViolation: false });

    test("markup written through an HTML sink is refused by Trusted Types", async ({
      page,
      cspViolations,
    }) => {
      await page.goto("/login");
      const refused = await page.evaluate(() => {
        try {
          document.body.innerHTML = '<img src="x" onerror="window.xss = 1">';
          return false;
        } catch {
          return true;
        }
      });
      expect(refused).toBe(true);
      await expect
        .poll(() => cspViolations.map((v) => v.detail).join("\n"))
        .toContain("require-trusted-types-for");
      expect(await page.evaluate(() => "xss" in window)).toBe(false);
    });

    test("an inline script, even one passed through a Trusted Types policy, does not run", async ({
      page,
      cspViolations,
    }) => {
      await page.goto("/login");
      await page.evaluate(() => {
        // TypeScript's DOM lib has no Trusted Types yet.
        const { trustedTypes } = window as unknown as {
          trustedTypes: {
            createPolicy(
              name: string,
              rules: { createScript(input: string): string },
            ): { createScript(input: string): string };
          };
        };
        const policy = trustedTypes.createPolicy("e2e-attacker", {
          createScript: (input) => input,
        });
        const script = document.createElement("script");
        script.text = policy.createScript("window.xss = 1");
        document.head.append(script);
      });
      await expect
        .poll(() => cspViolations.map((v) => v.detail).join("\n"))
        .toContain("script-src-elem blocked inline");
      expect(await page.evaluate(() => "xss" in window)).toBe(false);
    });
  });

  test("the external theme script applies a saved dark theme before the app loads", async ({
    page,
  }) => {
    await page.emulateMedia({ colorScheme: "light" });
    await page.addInitScript(() => localStorage.setItem("ui-theme", "dark"));
    // Without the app bundle, only the blocking theme script in <head> can set the class.
    await page.route("**/assets/**", (route) => route.abort());
    await page.goto("/login");
    await expect(page.locator("html")).toHaveClass(/\bdark\b/);
  });
});
