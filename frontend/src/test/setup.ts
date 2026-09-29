import '@testing-library/jest-dom';

// Reset fetch mock and cookies between tests.
beforeEach(() => {
  vi.restoreAllMocks();
  // Clear cookies
  document.cookie.split(';').forEach((c) => {
    document.cookie = c.replace(/^ +/, '').replace(/=.*/, '=;expires=' + new Date(0).toUTCString() + ';path=/');
  });
});
