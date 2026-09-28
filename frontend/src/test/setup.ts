// Vitest global setup: extend `expect` with jest-dom matchers and reset the
// DOM between tests. This file establishes Seam 2 (frontend component seam).
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => {
  cleanup();
});
