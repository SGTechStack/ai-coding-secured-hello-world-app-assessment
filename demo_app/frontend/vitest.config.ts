import { defineConfig, mergeConfig } from "vitest/config";
import viteConfig from "./vite.config.ts";

// vite.config.ts exports a function of the mode (it reads VITE_* for the CSP), so call it first.
export default defineConfig((configEnv) =>
  mergeConfig(viteConfig(configEnv), {
    test: {
      environment: "jsdom",
      // Playwright specs in e2e/ run with `npm run e2e`, not Vitest.
      include: ["src/**/*.test.{ts,tsx}"],
      setupFiles: ["./src/test/setup.ts"],
      coverage: {
        provider: "v8",
        include: ["src/**/*.{ts,tsx}"],
        exclude: ["src/main.tsx", "src/test/**", "src/**/*.test.{ts,tsx}"],
        thresholds: {
          lines: 80,
          branches: 80,
          functions: 80,
          statements: 80,
        },
      },
    },
  }),
);
