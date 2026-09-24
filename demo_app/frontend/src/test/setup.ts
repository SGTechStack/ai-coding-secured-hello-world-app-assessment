import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterAll, afterEach, beforeAll } from "vitest";
import { dropCsrfToken } from "../api/client";
import { server } from "./server";

// jsdom does not implement scrolling; TanStack Router's scroll handling calls it on navigation.
window.scrollTo = () => {};

// jsdom has <dialog> and its `open` state but not the modal API (AlertDialog uses it). This
// stand-in only toggles `open` and fires `close`, as the browser does; modality isn't modelled.
HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
  this.open = true;
};
HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
  if (!this.open) return;
  this.open = false;
  this.dispatchEvent(new Event("close"));
};

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));

afterEach(() => {
  cleanup();
  server.resetHandlers();
  // The in-memory CSRF token is module state and would otherwise leak between tests.
  dropCsrfToken();
});

afterAll(() => server.close());
