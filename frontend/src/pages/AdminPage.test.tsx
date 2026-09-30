import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { type FetchMock, stubFetchWithCsrf } from '../test/fetchMock';
import AdminPage from './AdminPage';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const USERS = [
  {
    id: 1,
    username: 'johndoe',
    email: 'johndoe@example.com',
    role: 'ADMIN',
    enabled: true,
    createdAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 2,
    username: 'janedoe',
    email: 'janedoe@example.com',
    role: 'USER',
    enabled: true,
    createdAt: '2026-01-02T00:00:00Z',
  },
];

function renderPage(currentUsername = 'johndoe') {
  return render(
    <MemoryRouter initialEntries={['/admin']}>
      <Routes>
        <Route path="/admin" element={<AdminPage currentUsername={currentUsername} />} />
        <Route path="/login" element={<div>Login page stub</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

function rowFor(username: string) {
  return screen.getByText(username).closest('tr')!;
}

describe('AdminPage', () => {
  let api: FetchMock;

  beforeEach(() => {
    api = stubFetchWithCsrf();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('loads and renders the user table', async () => {
    api.mockResolvedValue(jsonResponse(200, USERS));
    renderPage();

    expect(await screen.findByText('johndoe')).toBeInTheDocument();
    expect(screen.getByText('janedoe')).toBeInTheDocument();
    expect(api).toHaveBeenCalledWith('/api/admin/users', expect.objectContaining({ credentials: 'include' }));
  });

  it('disables the signed-in admin own row actions', async () => {
    api.mockResolvedValue(jsonResponse(200, USERS));
    renderPage('johndoe');
    await screen.findByText('johndoe');

    const ownRow = within(rowFor('johndoe'));
    expect(ownRow.getByRole('button', { name: 'Disable' })).toBeDisabled();
    expect(ownRow.getByRole('button', { name: /make user/i })).toBeDisabled();
    expect(ownRow.getByRole('button', { name: 'Delete' })).toBeDisabled();

    const otherRow = within(rowFor('janedoe'));
    expect(otherRow.getByRole('button', { name: 'Disable' })).not.toBeDisabled();
  });

  it('toggles a user enabled/disabled', async () => {
    api.mockResolvedValueOnce(jsonResponse(200, USERS));
    api.mockResolvedValueOnce(jsonResponse(200, { ...USERS[1], enabled: false }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('janedoe');

    await user.click(within(rowFor('janedoe')).getByRole('button', { name: 'Disable' }));

    expect(await within(rowFor('janedoe')).findByText('Disabled')).toBeInTheDocument();
    expect(api).toHaveBeenCalledWith(
      '/api/admin/users/2/status',
      expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ enabled: false }) }),
    );
  });

  it('toggles a user role', async () => {
    api.mockResolvedValueOnce(jsonResponse(200, USERS));
    api.mockResolvedValueOnce(jsonResponse(200, { ...USERS[1], role: 'ADMIN' }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('janedoe');

    await user.click(within(rowFor('janedoe')).getByRole('button', { name: /make admin/i }));

    expect(api).toHaveBeenCalledWith(
      '/api/admin/users/2/role',
      expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ role: 'ADMIN' }) }),
    );
  });

  it('deletes a user only after an inline confirmation step', async () => {
    api.mockResolvedValueOnce(jsonResponse(200, USERS));
    api.mockResolvedValueOnce(new Response(null, { status: 204 }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('janedoe');

    await user.click(within(rowFor('janedoe')).getByRole('button', { name: 'Delete' }));
    expect(api).toHaveBeenCalledTimes(1);
    expect(within(rowFor('janedoe')).getByRole('button', { name: 'Confirm delete' })).toBeInTheDocument();

    await user.click(within(rowFor('janedoe')).getByRole('button', { name: 'Confirm delete' }));

    expect(await screen.findByText('johndoe')).toBeInTheDocument();
    expect(screen.queryByText('janedoe')).not.toBeInTheDocument();
    expect(api).toHaveBeenCalledWith('/api/admin/users/2', expect.objectContaining({ method: 'DELETE' }));
  });

  it('cancels a pending delete confirmation without making a request', async () => {
    api.mockResolvedValue(jsonResponse(200, USERS));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('janedoe');

    await user.click(within(rowFor('janedoe')).getByRole('button', { name: 'Delete' }));
    await user.click(within(rowFor('janedoe')).getByRole('button', { name: 'Cancel' }));

    expect(within(rowFor('janedoe')).getByRole('button', { name: 'Delete' })).toBeInTheDocument();
    expect(api).toHaveBeenCalledTimes(1);
  });

  it('shows a fixed client message when an action is rejected (400 SELF_ACTION), not the server text', async () => {
    api.mockResolvedValueOnce(jsonResponse(200, USERS));
    api.mockResolvedValueOnce(jsonResponse(400, { code: 'SELF_ACTION', message: 'raw server text' }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('janedoe');

    await user.click(within(rowFor('janedoe')).getByRole('button', { name: /make admin/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent("You can't perform this action on your own account.");
    expect(screen.queryByText('raw server text')).not.toBeInTheDocument();
  });

  it('shows the last-admin guard message on a 400 LAST_ADMIN', async () => {
    api.mockResolvedValueOnce(jsonResponse(200, USERS));
    api.mockResolvedValueOnce(jsonResponse(400, { code: 'LAST_ADMIN', message: 'x' }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('janedoe');

    await user.click(within(rowFor('janedoe')).getByRole('button', { name: 'Disable' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('At least one enabled administrator must remain.');
  });

  it('shows a generic banner with role="alert" when the initial user list fails to load', async () => {
    api.mockRejectedValue(new TypeError('Failed to fetch'));
    renderPage();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Unable to connect to the server. Please try again later.',
    );
  });

  it('redirects to /login when the user list returns 401 (session expired)', async () => {
    api.mockResolvedValue(jsonResponse(401, {}));
    renderPage();

    expect(await screen.findByText('Login page stub')).toBeInTheDocument();
  });

  it('shows the permission message when the user list returns 403', async () => {
    api.mockResolvedValue(jsonResponse(403, { code: 'FORBIDDEN' }));
    renderPage();

    expect(await screen.findByRole('alert')).toHaveTextContent("You don't have permission to perform this action.");
  });

  it('redirects to /login when an action returns 401', async () => {
    api.mockResolvedValueOnce(jsonResponse(200, USERS));
    api.mockResolvedValueOnce(jsonResponse(401, {}));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('janedoe');

    await user.click(within(rowFor('janedoe')).getByRole('button', { name: 'Disable' }));

    expect(await screen.findByText('Login page stub')).toBeInTheDocument();
  });

  it('shows the permission message when an action still returns 403 after the CSRF retry', async () => {
    api.mockResolvedValueOnce(jsonResponse(200, USERS));
    api.mockResolvedValueOnce(jsonResponse(403, { code: 'CSRF_INVALID' }));
    api.mockResolvedValueOnce(jsonResponse(403, { code: 'FORBIDDEN' }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('janedoe');

    await user.click(within(rowFor('janedoe')).getByRole('button', { name: 'Disable' }));

    expect(await screen.findByRole('alert')).toHaveTextContent("You don't have permission to perform this action.");
    expect(api.mock.calls.filter(([url]) => url === '/api/admin/users/2/status')).toHaveLength(2);
  });

  it('reloads the list when an action returns 404 (the user no longer exists)', async () => {
    api.mockResolvedValueOnce(jsonResponse(200, USERS));
    api.mockResolvedValueOnce(jsonResponse(404, { code: 'NOT_FOUND' }));
    api.mockResolvedValueOnce(jsonResponse(200, [USERS[0]]));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('janedoe');

    await user.click(within(rowFor('janedoe')).getByRole('button', { name: 'Disable' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('That user no longer exists. The list has been refreshed.');
    await waitFor(() => expect(screen.queryByText('janedoe')).not.toBeInTheDocument());
    expect(api.mock.calls.filter(([url]) => url === '/api/admin/users')).toHaveLength(2);
  });
});
