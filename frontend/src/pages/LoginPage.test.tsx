import { act, fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { type FetchMock, stubFetchWithCsrf } from '../test/fetchMock';
import LoginPage from './LoginPage';

const VALID_USERNAME = 'johndoe';
const VALID_PASSWORD = 'Password123!';

function renderLoginPage(state?: unknown) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/login', state }]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<div>Landing page stub</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function usernameInput() {
  return screen.getByLabelText(/username/i);
}

function passwordInput() {
  // Exact match: the show/hide toggle button's aria-label also contains
  // "password" and would otherwise collide with a loose /password/i query.
  return screen.getByLabelText('Password');
}

function submitButton() {
  // Query by type, not accessible name: the name reads "Log in" normally but
  // "Logging in..." while a request is in flight, and a bare role query would
  // also match the password show/hide toggle button.
  return screen.getAllByRole('button').find((button) => button.getAttribute('type') === 'submit') as HTMLElement;
}

describe('LoginPage', () => {
  let api: FetchMock;

  beforeEach(() => {
    api = stubFetchWithCsrf();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('shows no validation errors while typing, before the first submit', async () => {
    const user = userEvent.setup();
    renderLoginPage();

    await user.type(usernameInput(), 'partial');

    expect(screen.queryByText('Username is required')).not.toBeInTheDocument();
    expect(screen.queryByText('Password is required')).not.toBeInTheDocument();
  });

  it('shows inline errors for empty fields on submit and makes no network request', async () => {
    renderLoginPage();

    fireEvent.submit(screen.getByRole('button', { name: /log in/i }).closest('form')!);

    expect(await screen.findByText('Username is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(api).not.toHaveBeenCalled();
  });

  it('dismisses an inline field error immediately when that field changes', async () => {
    renderLoginPage();
    const form = submitButton().closest('form')!;

    fireEvent.submit(form);
    expect(await screen.findByText('Username is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();

    fireEvent.change(usernameInput(), { target: { value: 'j' } });

    expect(screen.queryByText('Username is required')).not.toBeInTheDocument();
    // Password field wasn't touched, so its error is untouched too.
    expect(screen.getByText('Password is required')).toBeInTheDocument();
  });

  it('disables inputs/button and shows the "Logging in..." state while the request is in flight, held for a 400ms minimum', async () => {
    vi.useFakeTimers();

    let resolveFetch!: (value: Response) => void;
    api.mockImplementation(
      () => new Promise<Response>((resolve) => { resolveFetch = resolve; }),
    );

    renderLoginPage();

    fireEvent.change(usernameInput(), { target: { value: VALID_USERNAME } });
    fireEvent.change(passwordInput(), { target: { value: VALID_PASSWORD } });

    await act(async () => {
      fireEvent.submit(submitButton().closest('form')!);
      // Flush the microtask that kicks off login()+the min-delay timer.
      await Promise.resolve();
    });

    expect(usernameInput()).toBeDisabled();
    expect(passwordInput()).toBeDisabled();
    const button = submitButton();
    expect(button).toBeDisabled();
    expect(button.textContent).toMatch(/Logging in\.\.\./);

    // Server responds well within the 400ms floor...
    await act(async () => {
      resolveFetch(jsonResponse(200, {}));
      await Promise.resolve();
    });

    // ...but the loading state must still be held.
    expect(submitButton()).toBeDisabled();
    expect(screen.queryByText('Landing page stub')).not.toBeInTheDocument();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(400);
    });

    expect(screen.getByText('Landing page stub')).toBeInTheDocument();
  });

  it('authenticates and redirects to / on valid credentials', async () => {
    api.mockResolvedValue(jsonResponse(200, {}));
    const user = userEvent.setup();
    renderLoginPage();

    await user.type(usernameInput(), VALID_USERNAME);
    await user.type(passwordInput(), VALID_PASSWORD);
    await user.click(submitButton());

    expect(await screen.findByText('Landing page stub')).toBeInTheDocument();
    expect(api).toHaveBeenCalledWith('/api/auth/login', expect.objectContaining({ method: 'POST' }));
  });

  it('shows the generic invalid-credentials banner on a 401 and dismisses it on edit', async () => {
    api.mockResolvedValue(jsonResponse(401, { message: 'Invalid username or password' }));
    const user = userEvent.setup();
    renderLoginPage();

    await user.type(usernameInput(), VALID_USERNAME);
    await user.type(passwordInput(), 'wrong-password');
    await user.click(submitButton());

    const banner = await screen.findByText('Invalid username or password');
    expect(banner).toBeInTheDocument();

    fireEvent.change(passwordInput(), { target: { value: 'wrong-password2' } });
    expect(screen.queryByText('Invalid username or password')).not.toBeInTheDocument();
  });

  it('shows the server-unavailable banner on a 5xx response', async () => {
    api.mockResolvedValue(jsonResponse(503, {}));
    const user = userEvent.setup();
    renderLoginPage();

    await user.type(usernameInput(), VALID_USERNAME);
    await user.type(passwordInput(), VALID_PASSWORD);
    await user.click(submitButton());

    expect(
      await screen.findByText('Unable to connect to the server. Please try again later.'),
    ).toBeInTheDocument();
  });

  it('shows the server-unavailable banner on a network failure, dismissed by editing either field', async () => {
    api.mockRejectedValue(new TypeError('Failed to fetch'));
    const user = userEvent.setup();
    renderLoginPage();

    await user.type(usernameInput(), VALID_USERNAME);
    await user.type(passwordInput(), VALID_PASSWORD);
    await user.click(submitButton());

    expect(
      await screen.findByText('Unable to connect to the server. Please try again later.'),
    ).toBeInTheDocument();

    fireEvent.change(usernameInput(), { target: { value: `${VALID_USERNAME}2` } });
    expect(
      screen.queryByText('Unable to connect to the server. Please try again later.'),
    ).not.toBeInTheDocument();
  });

  it('re-enables the form after a failed attempt so the user can retry', async () => {
    api.mockResolvedValue(jsonResponse(401, { message: 'Invalid username or password' }));
    const user = userEvent.setup();
    renderLoginPage();

    await user.type(usernameInput(), VALID_USERNAME);
    await user.type(passwordInput(), 'wrong-password');
    await user.click(submitButton());

    await screen.findByText('Invalid username or password');
    expect(usernameInput()).not.toBeDisabled();
    expect(passwordInput()).not.toBeDisabled();
    expect(submitButton()).not.toBeDisabled();
  });

  it('shows the rate-limit message on a 429', async () => {
    api.mockResolvedValue(jsonResponse(429, { code: 'RATE_LIMITED', message: 'Too many for user johndoe' }));
    const user = userEvent.setup();
    renderLoginPage();

    await user.type(usernameInput(), VALID_USERNAME);
    await user.type(passwordInput(), VALID_PASSWORD);
    await user.click(submitButton());

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many attempts. Please try again later.');
    expect(screen.queryByText(/johndoe/)).not.toBeInTheDocument();
  });

  it('shows the same generic message on a 401 regardless of the server body', async () => {
    api.mockResolvedValue(jsonResponse(401, { code: 'INVALID_CREDENTIALS', message: 'Account locked' }));
    const user = userEvent.setup();
    renderLoginPage();

    await user.type(usernameInput(), VALID_USERNAME);
    await user.type(passwordInput(), VALID_PASSWORD);
    await user.click(submitButton());

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password');
    expect(screen.queryByText('Account locked')).not.toBeInTheDocument();
  });

  it('sends the CSRF token fetched from /api/auth/csrf on login', async () => {
    api.mockResolvedValue(jsonResponse(200, {}));
    const user = userEvent.setup();
    renderLoginPage();

    await user.type(usernameInput(), VALID_USERNAME);
    await user.type(passwordInput(), VALID_PASSWORD);
    await user.click(submitButton());

    await screen.findByText('Landing page stub');
    const [, init] = api.mock.calls.find(([url]) => url === '/api/auth/login')!;
    expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('test-csrf-token');
  });

  it('shows a success notice after registration (router state)', () => {
    renderLoginPage({ notice: 'registered' });

    expect(screen.getByRole('status')).toHaveTextContent('Account created. You can now log in.');
  });

  it('shows a success notice after a password reset (router state)', () => {
    renderLoginPage({ notice: 'passwordReset' });

    expect(screen.getByRole('status')).toHaveTextContent('Your password has been reset.');
  });

  it('ignores unknown router state rather than rendering it', () => {
    renderLoginPage({ notice: '<img src=x onerror=alert(1)>' });

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('sets maxLength on the inputs', () => {
    renderLoginPage();

    expect(usernameInput()).toHaveAttribute('maxLength', '64');
    expect(passwordInput()).toHaveAttribute('maxLength', '128');
  });
});
