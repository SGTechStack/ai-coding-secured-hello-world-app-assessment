// Vitest global setup: extend `expect` with jest-dom matchers and reset the
// DOM between tests. This file establishes Seam 2 (frontend component seam).
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';
import { clearCsrfToken } from '../api/apiClient';

afterEach(() => {
  cleanup();
  // The CSRF token is cached in module memory; never let it leak across tests.
  clearCsrfToken();
});
