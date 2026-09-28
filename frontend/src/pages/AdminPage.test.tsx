import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
      <AdminPage currentUsername={currentUsername} />
    </MemoryRouter>,
  );
}

function rowFor(username: string) {
  return screen.getByText(username).closest('tr')!;
}

describe('AdminPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('loads and renders the user table', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, USERS));
    renderPage();

    expect(await screen.findByText('johndoe')).toBeInTheDocument();
    expect(screen.getByText('janedoe')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/admin/users', expect.objectContaining({ credentials: 'include' }));
  });

  it('disables the signed-in admin own row actions', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, USERS));
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
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(200, USERS));
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(200, { ...USERS[1], enabled: false }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('janedoe');

    await user.click(within(rowFor('janedoe')).getByRole('button', { name: 'Disable' }));

    expect(await within(rowFor('janedoe')).findByText('Disabled')).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith(
      '/api/admin/users/2/status',
      expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ enabled: false }) }),
    );
  });

  it('toggles a user role', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(200, USERS));
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(200, { ...USERS[1], role: 'ADMIN' }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('janedoe');

    await user.click(within(rowFor('janedoe')).getByRole('button', { name: /make admin/i }));

    expect(fetch).toHaveBeenCalledWith(
      '/api/admin/users/2/role',
      expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ role: 'ADMIN' }) }),
    );
  });

  it('deletes a user only after an inline confirmation step', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(200, USERS));
    vi.mocked(fetch).mockResolvedValueOnce(new Response(null, { status: 204 }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('janedoe');

    await user.click(within(rowFor('janedoe')).getByRole('button', { name: 'Delete' }));
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(within(rowFor('janedoe')).getByRole('button', { name: 'Confirm delete' })).toBeInTheDocument();

    await user.click(within(rowFor('janedoe')).getByRole('button', { name: 'Confirm delete' }));

    expect(await screen.findByText('johndoe')).toBeInTheDocument();
    expect(screen.queryByText('janedoe')).not.toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith('/api/admin/users/2', expect.objectContaining({ method: 'DELETE' }));
  });

  it('cancels a pending delete confirmation without making a request', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, USERS));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('janedoe');

    await user.click(within(rowFor('janedoe')).getByRole('button', { name: 'Delete' }));
    await user.click(within(rowFor('janedoe')).getByRole('button', { name: 'Cancel' }));

    expect(within(rowFor('janedoe')).getByRole('button', { name: 'Delete' })).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it('shows the server-provided message when an action is rejected (400 self-action guard)', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(200, USERS));
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(400, { message: 'Cannot change your own role' }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('janedoe');

    await user.click(within(rowFor('janedoe')).getByRole('button', { name: /make admin/i }));

    expect(await screen.findByText('Cannot change your own role')).toBeInTheDocument();
  });

  it('shows a generic banner when the initial user list fails to load', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('Failed to fetch'));
    renderPage();

    expect(
      await screen.findByText('Unable to connect to the server. Please try again later.'),
    ).toBeInTheDocument();
  });
});
