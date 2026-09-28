import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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

function renderRegisterPage() {
  return render(
    <MemoryRouter initialEntries={['/register']}>
      <Routes>
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/login" element={<div>Login page stub</div>} />
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
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
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
    expect(fetch).not.toHaveBeenCalled();
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
    expect(fetch).not.toHaveBeenCalled();
  });

  it('registers and redirects to /login on success', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(201, {}));
    const user = userEvent.setup();
    renderRegisterPage();

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText('Login page stub')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith(
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

  it('shows the server-provided message on a 409 conflict and re-enables the form', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(409, { message: 'Username is already taken' }));
    const user = userEvent.setup();
    renderRegisterPage();

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText('Username is already taken')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create account/i })).not.toBeDisabled();
  });

  it('shows a generic server-unavailable message on a network failure', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'));
    const user = userEvent.setup();
    renderRegisterPage();

    await fillValidForm(user);
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(
      await screen.findByText('Unable to connect to the server. Please try again later.'),
    ).toBeInTheDocument();
  });
});
