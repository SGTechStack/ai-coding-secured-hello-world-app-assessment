import { test as base, expect } from "@playwright/test";

/** One CSP report, from a `securitypolicyviolation` event or the browser console. */
export type CspViolation = { source: "event" | "console"; detail: string };

type CspFixtures = {
  /** Every CSP violation seen in any page of the test's browser context, in order. */
  cspViolations: CspViolation[];
  /**
   * Whether a CSP violation fails the test (default `true`). Only a test that proves a violation
   * is reported, e.g. an injected script being blocked, turns this off and asserts on
   * `cspViolations` itself.
   */
  failOnCspViolation: boolean;
};

// Chrome logs "Refused to ... because it violates the following Content Security Policy
// directive" and "This document requires 'TrustedHTML' assignment" for Trusted Types.
const CSP_CONSOLE_ERROR =
  /Content Security Policy|Trusted ?(Types|HTML|Script)/i;

/**
 * The suite's `test`: every spec imports `test` and `expect` from here, not from
 * `@playwright/test`, so any CSP violation in the production build fails the test that caused it.
 * Playwright injects the listener and binding through the DevTools protocol, which the page's
 * CSP does not govern.
 */
export const test = base.extend<CspFixtures>({
  failOnCspViolation: [true, { option: true }],
  cspViolations: async ({}, use) => {
    await use([]);
  },
  context: async ({ context, cspViolations, failOnCspViolation }, use) => {
    await context.exposeBinding("__reportCspViolation", (_source, detail) => {
      cspViolations.push({ source: "event", detail: String(detail) });
    });
    await context.addInitScript(() => {
      document.addEventListener("securitypolicyviolation", (event) => {
        const report = (
          window as unknown as {
            __reportCspViolation: (detail: string) => Promise<void>;
          }
        ).__reportCspViolation;
        void report(
          `${event.effectiveDirective} blocked ${event.blockedURI || "inline"} ` +
            `(${event.sourceFile}:${event.lineNumber})`,
        );
      });
    });
    context.on("console", (message) => {
      if (
        message.type() === "error" &&
        CSP_CONSOLE_ERROR.test(message.text())
      ) {
        cspViolations.push({ source: "console", detail: message.text() });
      }
    });
    await use(context);
    if (failOnCspViolation) {
      expect(cspViolations, "CSP violations").toEqual([]);
    }
  },
});

export { expect };
