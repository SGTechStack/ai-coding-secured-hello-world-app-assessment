import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { type FetchMock, stubFetchWithCsrf } from '../test/fetchMock';
import RegisterPage from './RegisterPage';

const VALID_USERNAME = 'johndoe';
const VALID_EMAIL = 'johndoe@example.com';
const VALID_FIRST_NAME = 'John';
const VALID_PASSWORD = 'Password123!';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function LoginStub() {
  const { state } = useLocation();
  return <div data-notice={(state as { notice?: string } | null)?.notice ?? ''}>Login page stub</div>;
}

function renderRegisterPage() {
  return render(
    <MemoryRouter initialEntries={['/register']}>
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/login" element={<LoginStub />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Username'), VALID_USERNAME);
  await user.type(screen.getByLabelText('Email'), VALID_EMAIL);
  await user.type(screen.getByLabelText('First name'), VALID_FIRST_NAME);
  await user.type(screen.getByLabelText('Password'), VALID_PASSWORD);
}

describe('RegisterPage', () => {
  let api: FetchMock;

  beforeEach(() => {
    api = stubFetchWithCsrf();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows inline errors for empty fields on submit and makes no network request', async () => {
    renderRegisterPage();

    fireEvent.submit(screen.getByRole('button', { name: /create account/i }).closest('form')!);

    expect(await screen.findByText('Username is required')).toBeInTheDocument();
    expect(screen.getByText('Email is required')).toBeInTheDocument();
    expect(screen.getByText('First name is required')).toBeInTheDocument();
    expect(screen.getByText('Password is required')).toBeInTheDocument();
    expect(api).not.toHaveBeenCalled();
  });

  it('shows a password-length error without hitting the network when the password is too short', async () => {
    const user = userEvent.setup();
    renderRegisterPage();

    await user.type(screen.getByLabelText('Username'), VALID_USERNAME);
    await user.type(screen.getByLabelText('Email'), VALID_EMAIL);
    await user.type(screen.getByLabelText('First name'), VALID_FIRST_NAME);
    await user.type(screen.getByLabelText('Password'), 'short');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText('Password must be at least 12 characters long')).toBeInTheDocument();
    expect(api).not.toHaveBeenCalled();
  });

  it('registers and redirects to /login on success', async () => {
    api.mockResolvedValue(jsonResponse(201, {}));
    const user = userEvent.setup();
    renderRegisterPage();

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText('Login page stub')).toHaveAttribute('data-notice', 'registered');
    expect(api).toHaveBeenCalledWith(
      '/api/auth/register',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          username: VALID_USERNAME,
          email: VALID_EMAIL,
          password: VALID_PASSWORD,
          firstName: VALID_FIRST_NAME,
        }),
      }),
    );
  });

  it('shows the merged conflict message on a 409 (never the server text) and re-enables the form', async () => {
    api.mockResolvedValue(jsonResponse(409, { code: 'CONFLICT', message: 'Username is already taken' }));
    const user = userEvent.setup();
    renderRegisterPage();

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Username or email is already registered');
    expect(screen.queryByText('Username is already taken')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create account/i })).not.toBeDisabled();
  });

  it('shows the server validation message on a 400 VALIDATION_FAILED', async () => {
    api.mockResolvedValue(jsonResponse(400, { code: 'VALIDATION_FAILED', message: 'Email must be valid' }));
    const user = userEvent.setup();
    renderRegisterPage();

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Email must be valid');
  });

  it('shows the rate-limit message on a 429', async () => {
    api.mockResolvedValue(jsonResponse(429, { code: 'RATE_LIMITED' }));
    const user = userEvent.setup();
    renderRegisterPage();

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many attempts. Please try again later.');
  });

  it('rejects a password over 72 UTF-8 bytes client-side (73 bytes, 72 characters)', async () => {
    const user = userEvent.setup();
    renderRegisterPage();
    const password = `${'a'.repeat(71)}é`;
    expect(new TextEncoder().encode(password).length).toBe(73);

    await user.type(screen.getByLabelText('Username'), VALID_USERNAME);
    await user.type(screen.getByLabelText('Email'), VALID_EMAIL);
    await user.type(screen.getByLabelText('First name'), VALID_FIRST_NAME);
    await user.type(screen.getByLabelText('Password'), password);
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText(/Password is too long/)).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('accepts a password of exactly 72 UTF-8 bytes', async () => {
    api.mockResolvedValue(jsonResponse(201, {}));
    const user = userEvent.setup();
    renderRegisterPage();

    await user.type(screen.getByLabelText('Username'), VALID_USERNAME);
    await user.type(screen.getByLabelText('Email'), VALID_EMAIL);
    await user.type(screen.getByLabelText('First name'), VALID_FIRST_NAME);
    await user.type(screen.getByLabelText('Password'), `${'a'.repeat(70)}é`);
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText('Login page stub')).toBeInTheDocument();
  });

  it('validates username format, email format and first-name length without hitting the network', async () => {
    renderRegisterPage();

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'ab' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'not-an-email' } });
    fireEvent.change(screen.getByLabelText('First name'), { target: { value: 'x'.repeat(101) } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: VALID_PASSWORD } });
    fireEvent.submit(screen.getByRole('button', { name: /create account/i }).closest('form')!);

    expect(await screen.findByText(/Username must be 3-64 characters/)).toBeInTheDocument();
    expect(screen.getByText('Enter a valid email address')).toBeInTheDocument();
    expect(screen.getByText('First name must be at most 100 characters')).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('rejects a username with disallowed characters', async () => {
    renderRegisterPage();

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'john doe' } });
    fireEvent.submit(screen.getByRole('button', { name: /create account/i }).closest('form')!);

    expect(await screen.findByText(/Username must be 3-64 characters/)).toBeInTheDocument();
  });

  it('sets maxLength on every input', () => {
    renderRegisterPage();

    expect(screen.getByLabelText('Username')).toHaveAttribute('maxLength', '64');
    expect(screen.getByLabelText('Email')).toHaveAttribute('maxLength', '254');
    expect(screen.getByLabelText('First name')).toHaveAttribute('maxLength', '100');
    expect(screen.getByLabelText('Password')).toHaveAttribute('maxLength', '128');
  });

  it('shows a generic server-unavailable message on a network failure', async () => {
    api.mockRejectedValue(new TypeError('Failed to fetch'));
    const user = userEvent.setup();
    renderRegisterPage();

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(
      await screen.findByText('Unable to connect to the server. Please try again later.'),
    ).toBeInTheDocument();
  });
});
