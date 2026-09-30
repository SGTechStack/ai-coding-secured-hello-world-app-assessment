import '@testing-library/jest-dom/vitest';

// jsdom has no modal <dialog>: stand in for showModal/close by toggling `open`. The browser's own behaviour (backdrop,
// inert page, focus containment, Esc) is covered by the Playwright specs. Node-environment tests have no DOM at all.
if (typeof HTMLDialogElement !== 'undefined') {
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.open = true;
  };
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    this.open = false;
  };
}
