/** Where the end-to-end run serves each part. The API's dev-profile CORS allow-list names the SPA's. */
export const SPA_ORIGIN = 'http://localhost:5173';
export const API_ORIGIN = 'http://localhost:8080';
/**
 * Stands in for the Have I Been Pwned range API, so the run needs no network. An IP rather than
 * `localhost`, which Node and Java may resolve to different address families.
 */
export const PWNED_PASSWORDS_ORIGIN = 'http://127.0.0.1:8089';
