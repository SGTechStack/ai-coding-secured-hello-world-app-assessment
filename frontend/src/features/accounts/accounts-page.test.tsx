import { Suspense } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AccountsPage } from './accounts-page';

const fetchMock = vi.fn();

function jsonResponse({ status, body }: { status: number; body: unknown }) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const ROOT = { id: 'id-root', username: 'root', role: 'ADMIN', enabled: true, createdAt: '2030-01-02T03:04:05Z' };
const SUSPENDED = {
  id: 'id-suspended',
  username: 'suspended',
  role: 'USER',
  enabled: false,
  createdAt: '2030-02-03T04:05:06Z',
};

const ACTIVE = {
  id: 'id-active',
  username: 'active-user',
  role: 'USER',
  enabled: true,
  createdAt: '2030-03-04T05:06:07Z',
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <Suspense fallback={<p>Loading</p>}>
        <AccountsPage />
      </Suspense>
    </QueryClientProvider>,
  );
}

async function openCreateDialog({ user }: { user: ReturnType<typeof userEvent.setup> }) {
  await user.click(screen.getByRole('button', { name: 'Add account' }));
  return screen.findByRole('dialog');
}

async function openRoleTab({ role }: { role: string }) {
  await userEvent.click(await screen.findByRole('tab', { name: new RegExp(`^${role} `) }));
}

function requestsTo({ method }: { method: string }) {
  return fetchMock.mock.calls.filter(([, init]) => ((init as RequestInit | undefined)?.method ?? 'GET') === method);
}

describe('AccountsPage', () => {
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('lists every Account with username, role, enabled state and created date', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ status: 200, body: [ROOT, SUSPENDED] }));
    renderPage();

    await openRoleTab({ role: 'ADMIN' });
    const rootRow = (await screen.findByText('root')).closest('tr')!;
    expect(within(rootRow).getByRole('combobox', { name: 'Role for root' })).toHaveTextContent('ADMIN');
    expect(within(rootRow).getByText('Enabled')).toBeInTheDocument();
    expect(within(rootRow).getByText(/2030/)).toBeInTheDocument();
    await openRoleTab({ role: 'USER' });
    const suspendedRow = (await screen.findByText('suspended')).closest('tr')!;
    expect(within(suspendedRow).getByText('Disabled')).toBeInTheDocument();
    expect(fetchMock.mock.calls[0][0]).toBe('/admin/api/users');
  });

  it('creates an Account, shows its Temporary Password once with a notice and refreshes the list', async () => {
    const created = {
      username: 'new-hire',
      role: 'ADMIN',
      temporaryPassword: 'Xk7-temp-Pass-42',
      temporaryPasswordExpiresAt: '2030-01-03T03:04:05Z',
    };
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ status: 200, body: [ROOT] }))
      .mockResolvedValueOnce(jsonResponse({ status: 201, body: created }))
      .mockResolvedValueOnce(
        jsonResponse({
          status: 200,
          body: [ROOT, { ...SUSPENDED, username: 'new-hire', role: 'ADMIN', enabled: true }],
        }),
      );
    renderPage();
    await openRoleTab({ role: 'ADMIN' });
    await screen.findByText('root');
    const user = userEvent.setup();

    const dialog = await openCreateDialog({ user });
    await user.type(within(dialog).getByLabelText('Username'), 'new-hire');
    await user.click(within(dialog).getByRole('combobox', { name: 'Role' }));
    await user.click(await screen.findByRole('option', { name: 'ADMIN' }));
    await user.click(within(dialog).getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Xk7-temp-Pass-42')).toBeInTheDocument();
    expect(screen.getByText(/shown only once/i)).toBeInTheDocument();
    const [, init] = requestsTo({ method: 'POST' })[0] as [string, RequestInit];
    expect(JSON.parse(init.body as string)).toEqual({ username: 'new-hire', role: 'ADMIN' });
    await openRoleTab({ role: 'ADMIN' });
    expect(await screen.findByText('new-hire', { selector: 'td' })).toBeInTheDocument();
  });

  it('refuses a duplicate username with the server message and no Temporary Password', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ status: 200, body: [ROOT] }))
      .mockResolvedValueOnce(
        jsonResponse({ status: 409, body: { title: 'Conflict', detail: 'Username is already taken' } }),
      );
    renderPage();
    await openRoleTab({ role: 'ADMIN' });
    await screen.findByText('root');
    const user = userEvent.setup();

    const dialog = await openCreateDialog({ user });
    await user.type(within(dialog).getByLabelText('Username'), 'root');
    await user.click(within(dialog).getByRole('button', { name: 'Create account' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Username is already taken');
    expect(screen.queryByText(/shown only once/i)).not.toBeInTheDocument();
  });

  it('asks for a username without calling the API when it is empty', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ status: 200, body: [ROOT] }));
    renderPage();
    await openRoleTab({ role: 'ADMIN' });
    await screen.findByText('root');
    const user = userEvent.setup();

    const dialog = await openCreateDialog({ user });
    await user.click(within(dialog).getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Enter a username')).toBeInTheDocument();
    expect(requestsTo({ method: 'POST' })).toHaveLength(0);
  });

  it('resets an Account password from its row and shows the new Temporary Password once', async () => {
    const reset = {
      username: 'suspended',
      role: 'USER',
      temporaryPassword: 'Rs9-reset-Pass-77',
      temporaryPasswordExpiresAt: '2030-01-03T03:04:05Z',
    };
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ status: 200, body: [ROOT, SUSPENDED] }))
      .mockResolvedValueOnce(jsonResponse({ status: 200, body: reset }));
    renderPage();
    const row = (await screen.findByText('suspended')).closest('tr')!;
    const user = userEvent.setup();

    await user.click(within(row).getByRole('button', { name: 'Reset password' }));

    expect(await screen.findByText('Rs9-reset-Pass-77')).toBeInTheDocument();
    expect(screen.getByText(/shown only once/i)).toBeInTheDocument();
    expect(screen.getByText(/Password reset for suspended/)).toBeInTheDocument();
    const [url, init] = requestsTo({ method: 'POST' })[0] as [string, RequestInit];
    expect(url).toBe('/admin/api/users/id-suspended/reset-password');
    expect(init.body).toBeUndefined();
  });

  it('shows the server reason and no Temporary Password when a reset is refused', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ status: 200, body: [ROOT] }))
      .mockResolvedValueOnce(
        jsonResponse({ status: 403, body: { title: 'Forbidden', detail: 'Admins cannot reset their own password' } }),
      );
    renderPage();
    await openRoleTab({ role: 'ADMIN' });
    const row = (await screen.findByText('root')).closest('tr')!;
    const user = userEvent.setup();

    await user.click(within(row).getByRole('button', { name: 'Reset password' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Admins cannot reset their own password');
    expect(screen.queryByText(/shown only once/i)).not.toBeInTheDocument();
  });

  it('disables an Account from its row and refreshes the list', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ status: 200, body: [ROOT, ACTIVE] }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse({ status: 200, body: [ROOT, { ...ACTIVE, enabled: false }] }));
    renderPage();
    const row = (await screen.findByText('active-user')).closest('tr')!;
    const user = userEvent.setup();

    await user.click(within(row).getByRole('button', { name: 'Disable' }));

    expect(await within(screen.getByText('active-user').closest('tr')!).findByText('Disabled')).toBeInTheDocument();
    const [url, init] = requestsTo({ method: 'PUT' })[0] as [string, RequestInit];
    expect(url).toBe('/admin/api/users/id-active/enabled');
    expect(JSON.parse(init.body as string)).toEqual({ enabled: false });
  });

  it('shows the server reason when an action is refused', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ status: 200, body: [ROOT] }))
      .mockResolvedValueOnce(
        jsonResponse({ status: 409, body: { title: 'Conflict', detail: 'You cannot disable your own account' } }),
      );
    renderPage();
    await openRoleTab({ role: 'ADMIN' });
    const row = (await screen.findByText('root')).closest('tr')!;
    const user = userEvent.setup();

    await user.click(within(row).getByRole('button', { name: 'Disable' }));

    expect(await within(row).findByRole('alert')).toHaveTextContent('You cannot disable your own account');
    expect(within(row).getByText('Enabled')).toBeInTheDocument();
  });

  it('changes an Account Role from its row and refreshes the list', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ status: 200, body: [ROOT, ACTIVE] }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse({ status: 200, body: [ROOT, { ...ACTIVE, role: 'ADMIN' }] }));
    renderPage();
    await screen.findByText('active-user');
    const user = userEvent.setup();

    await user.click(screen.getByRole('combobox', { name: 'Role for active-user' }));
    await user.click(await screen.findByRole('option', { name: 'ADMIN' }));

    await waitFor(() => expect(screen.queryByText('active-user')).not.toBeInTheDocument());
    await openRoleTab({ role: 'ADMIN' });
    expect(await screen.findByRole('combobox', { name: 'Role for active-user' })).toHaveTextContent('ADMIN');
    const [url, init] = requestsTo({ method: 'PUT' })[0] as [string, RequestInit];
    expect(url).toBe('/admin/api/users/id-active/role');
    expect(JSON.parse(init.body as string)).toEqual({ role: 'ADMIN' });
  });

  it('deletes an Account only after confirmation and refreshes the list', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ status: 200, body: [ROOT, ACTIVE] }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse({ status: 200, body: [ROOT] }));
    renderPage();
    const row = (await screen.findByText('active-user')).closest('tr')!;
    const user = userEvent.setup();

    await user.click(within(row).getByRole('button', { name: 'Delete active-user' }));
    let dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/Delete active-user permanently/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(requestsTo({ method: 'DELETE' })).toHaveLength(0);

    await user.click(within(row).getByRole('button', { name: 'Delete active-user' }));
    dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(screen.queryByText('active-user')).not.toBeInTheDocument());
    const [url] = requestsTo({ method: 'DELETE' })[0] as [string, RequestInit];
    expect(url).toBe('/admin/api/users/id-active');
  });

  it('shows a tooltip on hover for each row action', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ status: 200, body: [ACTIVE] }));
    renderPage();
    const row = (await screen.findByText('active-user')).closest('tr')!;
    const user = userEvent.setup();

    for (const name of ['Reset password', 'Disable', 'Delete active-user']) {
      await user.hover(within(row).getByRole('button', { name }));
      expect(await screen.findByText(name, { selector: '[data-side]' })).toBeInTheDocument();
      await user.unhover(within(row).getByRole('button', { name }));
    }
  });

  it('pages a Role tab ten accounts at a time', async () => {
    const many = Array.from({ length: 12 }, (_, n) => ({
      ...ACTIVE,
      id: `id-${n}`,
      username: `user-${String(n).padStart(2, '0')}`,
    }));
    fetchMock.mockResolvedValue(jsonResponse({ status: 200, body: many }));
    renderPage();
    await screen.findByText('user-00');
    const user = userEvent.setup();

    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument();
    expect(screen.queryByText('user-10')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Next' }));

    expect(screen.getByText('Page 2 of 2')).toBeInTheDocument();
    expect(screen.getByText('user-10')).toBeInTheDocument();
    expect(screen.queryByText('user-00')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
  });

  it('splits accounts into one tab per Role', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ status: 200, body: [ROOT, ACTIVE] }));
    renderPage();

    expect(await screen.findByRole('tab', { name: 'USER (1)' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'ADMIN (1)' })).toBeInTheDocument();
    expect(screen.getAllByRole('tab')).toHaveLength(2);
    expect(screen.queryByText('root')).not.toBeInTheDocument();
    await openRoleTab({ role: 'ADMIN' });
    expect(await screen.findByText('root')).toBeInTheDocument();
  });
});
