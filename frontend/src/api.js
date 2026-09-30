// Thin API client for the Spring Boot backend.
// - Always sends credentials (session cookie travels cross-origin).
// - Reads the XSRF-TOKEN cookie and echoes it as the X-XSRF-TOKEN header on
//   state-changing requests, matching Spring's CookieCsrfTokenRepository.

const BASE_URL = 'http://localhost:8080';

function getCookie(name) {
  const match = document.cookie.match(new RegExp('(^| )' + name + '=([^;]+)'));
  return match ? decodeURIComponent(match[2]) : null;
}

// Prime the CSRF cookie by hitting a permitted GET first (any GET sets it).
export async function primeCsrf() {
  try {
    await fetch(`${BASE_URL}/api/hello`, { credentials: 'include' });
  } catch {
    // ignore — we only care about the Set-Cookie side effect
  }
}

async function request(method, path, body) {
  const headers = { 'Content-Type': 'application/json' };
  const csrf = getCookie('XSRF-TOKEN');
  if (csrf) {
    headers['X-XSRF-TOKEN'] = csrf;
  }

  const res = await fetch(`${BASE_URL}${path}`, {
    method,
    credentials: 'include',
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  let data = null;
  const text = await res.text();
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = { message: text };
    }
  }

  if (!res.ok) {
    const message = (data && data.message) || `Request failed (${res.status})`;
    const error = new Error(message);
    error.status = res.status;
    throw error;
  }
  return data;
}

export const api = {
  get: (path) => request('GET', path),
  post: (path, body) => request('POST', path, body),
  put: (path, body) => request('PUT', path, body),
  del: (path) => request('DELETE', path),
};

export { BASE_URL };
