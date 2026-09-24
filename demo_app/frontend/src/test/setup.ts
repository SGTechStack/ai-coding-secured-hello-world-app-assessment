import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterAll, afterEach, beforeAll } from "vitest";
import { dropCsrfToken } from "../api/client";
import { server } from "./server";

// jsdom does not implement scrolling; TanStack Router's scroll handling calls it on navigation.
window.scrollTo = () => {};

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));

afterEach(() => {
  cleanup();
  server.resetHandlers();
  // The in-memory CSRF token is module state and would otherwise leak between tests.
  dropCsrfToken();
});

afterAll(() => server.close());
