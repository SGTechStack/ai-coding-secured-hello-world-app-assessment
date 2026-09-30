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

    const rootRow = (await screen.findByText('root')).closest('tr')!;
    expect(within(rootRow).getByLabelText('Role for root')).toHaveValue('ADMIN');
    expect(within(rootRow).getByText('Enabled')).toBeInTheDocument();
    expect(within(rootRow).getByText(/2030/)).toBeInTheDocument();
    const suspendedRow = screen.getByText('suspended').closest('tr')!;
    expect(within(suspendedRow).getByText('Disabled')).toBeInTheDocument();
    expect(fetchMock.mock.calls[0][0]).toBe('/admin/api/users');
  });

  it('creates an Account, shows its Temporary Password once with a notice and refreshes the list', async () => {
    const created = {
      username: 'new-hire',
      role: 'USER_MANAGER',
      temporaryPassword: 'Xk7-temp-Pass-42',
      temporaryPasswordExpiresAt: '2030-01-03T03:04:05Z',
    };
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ status: 200, body: [ROOT] }))
      .mockResolvedValueOnce(jsonResponse({ status: 201, body: created }))
      .mockResolvedValueOnce(
        jsonResponse({
          status: 200,
          body: [ROOT, { ...SUSPENDED, username: 'new-hire', role: 'USER_MANAGER', enabled: true }],
        }),
      );
    renderPage();
    await screen.findByText('root');
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Username'), 'new-hire');
    await user.selectOptions(screen.getByLabelText('Role'), 'USER_MANAGER');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Xk7-temp-Pass-42')).toBeInTheDocument();
    expect(screen.getByText(/shown only once/i)).toBeInTheDocument();
    const [, init] = requestsTo({ method: 'POST' })[0] as [string, RequestInit];
    expect(JSON.parse(init.body as string)).toEqual({ username: 'new-hire', role: 'USER_MANAGER' });
    expect(await screen.findByText('new-hire', { selector: 'td' })).toBeInTheDocument();
  });

  it('refuses a duplicate username with the server message and no Temporary Password', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ status: 200, body: [ROOT] }))
      .mockResolvedValueOnce(
        jsonResponse({ status: 409, body: { title: 'Conflict', detail: 'Username is already taken' } }),
      );
    renderPage();
    await screen.findByText('root');
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Username'), 'root');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Username is already taken');
    expect(screen.queryByText(/shown only once/i)).not.toBeInTheDocument();
  });

  it('asks for a username without calling the API when it is empty', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ status: 200, body: [ROOT] }));
    renderPage();
    await screen.findByText('root');
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Enter a username')).toBeInTheDocument();
    expect(requestsTo({ method: 'POST' })).toHaveLength(0);
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
      .mockResolvedValueOnce(jsonResponse({ status: 200, body: [ROOT, { ...ACTIVE, role: 'USER_MANAGER' }] }));
    renderPage();
    await screen.findByText('active-user');
    const user = userEvent.setup();

    await user.selectOptions(screen.getByLabelText('Role for active-user'), 'USER_MANAGER');

    await waitFor(() => expect(screen.getByLabelText('Role for active-user')).toHaveValue('USER_MANAGER'));
    const [url, init] = requestsTo({ method: 'PUT' })[0] as [string, RequestInit];
    expect(url).toBe('/admin/api/users/id-active/role');
    expect(JSON.parse(init.body as string)).toEqual({ role: 'USER_MANAGER' });
  });

  it('deletes an Account only after confirmation and refreshes the list', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ status: 200, body: [ROOT, ACTIVE] }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse({ status: 200, body: [ROOT] }));
    renderPage();
    const row = (await screen.findByText('active-user')).closest('tr')!;
    const user = userEvent.setup();

    await user.click(within(row).getByRole('button', { name: 'Delete' }));
    expect(within(row).getByText('Delete active-user permanently?')).toBeInTheDocument();
    await user.click(within(row).getByRole('button', { name: 'Cancel' }));
    expect(requestsTo({ method: 'DELETE' })).toHaveLength(0);

    await user.click(within(row).getByRole('button', { name: 'Delete' }));
    await user.click(within(row).getByRole('button', { name: 'Confirm delete' }));

    await waitFor(() => expect(screen.queryByText('active-user')).not.toBeInTheDocument());
    const [url] = requestsTo({ method: 'DELETE' })[0] as [string, RequestInit];
    expect(url).toBe('/admin/api/users/id-active');
  });
});
