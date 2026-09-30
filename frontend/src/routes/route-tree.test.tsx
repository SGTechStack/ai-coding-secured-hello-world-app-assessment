import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createMemoryHistory, createRouter, RouterProvider } from '@tanstack/react-router';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { apiClient } from '../common/http/api-client';
import { resetCsrfToken, type CsrfResponse } from '../common/http/csrf';
import type { ProblemDetailJson } from '../common/http/problem-detail';
import type { Profile } from '../features/account/profile';
import type { Greeting } from '../features/account/greeting/api/greeting-api';
import type { ListedAccount, UserListPage } from '../features/account/administration/model/user-list';
import { routeTree } from '../routeTree.gen';
import { csrfToken, fakeAdapter, JOHNDOE, type FakeReply } from '../test-support';

/** The real route tree in a memory-history router, behind a fake server that holds a live Session or none. */
type Caller = 'visitor' | 'user' | 'admin';
type Reply = FakeReply<Profile | CsrfResponse | Greeting | UserListPage | ListedAccount | string[] | ProblemDetailJson>;

const ADMIN_ID = '7d2f6a52-1c4e-4a0e-9d57-4d0c3b1f2e11';
let calls: string[];
let caller: Caller;
/** Answers `GET /api/admin/users` for a 0-based page. */
let userList: (page: number) => Reply | Promise<Reply>;
/** Answers `PATCH /api/admin/users/{id}` given the target id and the requested enabled state. */
let toggle: (id: string, enabled: boolean) => Reply | Promise<Reply>;
/** Answers `DELETE /api/admin/users/{id}` given the target id. */
let remove: (id: string) => Reply | Promise<Reply>;
/** Answers `GET /api/admin/roles`. */
let roles: () => Reply | Promise<Reply>;
/** Answers `PUT /api/admin/users/{id}/role` given the target id and the requested role. */
let changeRole: (id: string, role: string) => Reply | Promise<Reply>;

/** A User list of `total` Accounts, 20 per page: the Admin first, then member001, member002, ... */
function listPage(page: number, total = 45): UserListPage {
  const accounts = Array.from({ length: total }, (_, index): ListedAccount => ({
    id: index === 0 ? ADMIN_ID : `account-${index}`,
    username: index === 0 ? 'janeadmin' : `member${String(index).padStart(3, '0')}`,
    email: index === 1 ? null : `user${index}@test.example.com`,
    role: index === 0 ? 'ADMIN' : 'USER',
    enabled: true,
    deleted: false,
    locked: false,
    failedLoginAttempts: 0,
    lockedUntil: null,
    disabledAt: null,
    deletedAt: null,
    lastLoginAt: index === 1 ? null : '2026-09-29T01:14:00Z',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  }));
  return {
    content: accounts.slice(page * 20, page * 20 + 20),
    page: { size: 20, number: page, totalElements: total, totalPages: Math.ceil(total / 20) },
  };
}

/** The row a successful toggle returns: the member for `id` (account-NNN -> memberNNN) in its new enabled state. */
function toggledRow(id: string, enabled: boolean): ListedAccount {
  const index = Number(id.slice('account-'.length));
  return {
    id,
    username: `member${String(index).padStart(3, '0')}`,
    email: `user${index}@test.example.com`,
    role: 'USER',
    enabled,
    deleted: false,
    locked: false,
    failedLoginAttempts: 0,
    lockedUntil: null,
    disabledAt: enabled ? null : '2026-09-30T00:00:00Z',
    deletedAt: null,
    lastLoginAt: '2026-09-29T01:14:00Z',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-09-30T00:00:00Z',
  };
}

function serve(): void {
  apiClient.defaults.adapter = fakeAdapter(async (config): Promise<Reply> => {
    const params = config.params as { page?: number } | undefined;
    const query = params?.page === undefined ? '' : `?page=${params.page}`;
    const request = `${config.method?.toUpperCase()} ${config.url}${query}`;
    calls.push(request);
    if (caller === 'visitor') return { status: 401, data: { code: 'AUTHENTICATION_REQUIRED' } };
    if (config.url === '/api/admin/users') return await userList(params?.page ?? 0);
    if (config.url === '/api/admin/roles') return await roles();
    if (config.method?.toUpperCase() === 'PUT' && config.url?.endsWith('/role')) {
      const id = config.url.slice('/api/admin/users/'.length, -'/role'.length);
      return await changeRole(id, (JSON.parse(config.data as string) as { role: string }).role);
    }
    if (config.method?.toUpperCase() === 'PATCH' && config.url?.startsWith('/api/admin/users/')) {
      const id = config.url.slice('/api/admin/users/'.length);
      const enabled = (JSON.parse(config.data as string) as { enabled: boolean }).enabled;
      return await toggle(id, enabled);
    }
    if (config.method?.toUpperCase() === 'DELETE' && config.url?.startsWith('/api/admin/users/')) {
      return await remove(config.url.slice('/api/admin/users/'.length));
    }
    switch (request) {
      case 'GET /api/profile':
        return {
          status: 200,
          data: caller === 'admin' ? { id: ADMIN_ID, role: 'ADMIN' } : { id: JOHNDOE.id, role: 'USER' },
        };
      case 'GET /csrf':
        return { status: 200, data: csrfToken() };
      case 'GET /api/hello':
        return { status: 200, data: { message: caller === 'admin' ? 'Hello, janeadmin' : 'Hello, johndoe' } };
      case 'POST /api/auth/logout':
        return { status: 204 };
      default:
        throw new Error(`unexpected request ${request}`);
    }
  });
}

async function open(path: string) {
  const queryClient = new QueryClient();
  const router = createRouter({
    routeTree,
    context: { queryClient },
    history: createMemoryHistory({ initialEntries: [path] }),
  });
  await act(async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    );
    await router.load();
  });
  return Object.assign(router, { queryClient });
}

const original = apiClient.defaults.adapter;
beforeEach(() => {
  calls = [];
  userList = (page) => ({ status: 200, data: listPage(page) });
  // Default: the toggle succeeds, returning the member's row (username memberNNN from account-NNN) in its new state.
  toggle = (id, enabled) => ({ status: 200, data: toggledRow(id, enabled) });
  remove = () => ({ status: 204 });
  roles = () => ({ status: 200, data: ['USER', 'ADMIN'] });
  changeRole = (id, role) => ({ status: 200, data: { ...toggledRow(id, true), role } });
  serve();
});
afterEach(() => {
  cleanup();
  resetCsrfToken();
  apiClient.defaults.adapter = original;
});

describe('route tree', () => {
  it.each<Caller>(['visitor', 'user'])(
    'opening / ends on the right page for a %s, restoring the Session once',
    async (who) => {
      caller = who;
      const router = await open('/');

      if (who === 'user') {
        expect(await screen.findByRole('heading', { level: 1, name: 'Hello, johndoe' })).toBeVisible();
        expect(router.state.location.pathname).toBe('/home');
      } else {
        expect(await screen.findByRole('heading', { name: 'Log in' })).toBeVisible();
        expect(router.state.location.pathname).toBe('/login');
      }
      expect(calls.filter((call) => call === 'GET /api/profile')).toHaveLength(1);
    },
  );

  it.each<[Caller, string, string]>([
    ['visitor', 'Log in', '/login'],
    ['user', 'Hello, johndoe', '/home'],
  ])('a %s who opens or reloads /home sees "%s"', async (who, heading, pathname) => {
    caller = who;
    const router = await open('/home');

    expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeVisible();
    expect(router.state.location.pathname).toBe(pathname);
  });

  it.each<[Caller, string]>([
    ['visitor', '/nope'],
    ['user', '/nope'],
    ['visitor', '/some/deep/path'],
    ['user', '/images-old.png'],
    // The retired address of the Home page keeps no redirect (ADR 0007 amendment).
    ['visitor', '/welcome'],
    ['user', '/welcome'],
  ])(
    'a %s who opens %s sees Page not found, outside the authenticated layout and without calling the API',
    async (who, path) => {
      caller = who;
      const router = await open(path);

      expect(screen.getByRole('heading', { level: 1, name: 'Page not found' })).toBeVisible();
      expect(screen.getByRole('link', { name: 'Go to the home page' })).toHaveAttribute('href', '/home');
      expect(screen.getByRole('link', { name: 'Go to the home page' })).toHaveClass('min-h-11', 'min-w-11');
      expect(screen.queryByRole('button', { name: 'Log out' })).not.toBeInTheDocument();
      expect(calls).toEqual([]);
      expect(router.state.location.pathname).toBe(path);
    },
  );

  it.each<[Caller, string, string]>([
    ['visitor', 'Log in', '/login'],
    ['user', 'Hello, johndoe', '/home'],
  ])('the Page not found link takes a %s to "%s"', async (who, heading, pathname) => {
    caller = who;
    const router = await open('/nope');

    fireEvent.click(screen.getByRole('link', { name: 'Go to the home page' }));

    expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeVisible();
    expect(router.state.location.pathname).toBe(pathname);
  });
});

describe('admin navigation and the User list', () => {
  const mainNav = () => screen.queryByRole('navigation', { name: 'Main' });
  const table = () => screen.getByRole('table');
  const rowOf = (username: string) => within(table()).getByRole('row', { name: new RegExp(`^${username}\\b`) });
  const userListCalls = () => calls.filter((call) => call.startsWith('GET /api/admin/users'));
  const pageLabel = () => within(screen.getByRole('navigation', { name: 'Pagination' })).getByText(/^Page/);

  it('shows an Admin the navigation row, with Home current on /home, and Users opens the first page', async () => {
    caller = 'admin';
    const router = await open('/home');

    expect(await screen.findByRole('heading', { level: 1, name: 'Hello, janeadmin' })).toBeVisible();
    const nav = within(mainNav()!);
    expect(nav.getByRole('link', { name: 'Home' })).toHaveAttribute('aria-current', 'page');
    expect(nav.getByRole('link', { name: 'Users' })).not.toHaveAttribute('aria-current');

    fireEvent.click(nav.getByRole('link', { name: 'Users' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Users' })).toBeVisible();
    expect(await screen.findByRole('table')).toBeVisible();
    expect(router.state.location.pathname).toBe('/admin/users');
    expect(nav.getByRole('link', { name: 'Users' })).toHaveAttribute('aria-current', 'page');
    expect(nav.getByRole('link', { name: 'Home' })).not.toHaveAttribute('aria-current');
    expect(userListCalls()).toEqual(['GET /api/admin/users?page=0']);
  });

  it('shows a User no navigation row and no Users link', async () => {
    caller = 'user';
    await open('/home');

    expect(await screen.findByRole('heading', { level: 1, name: 'Hello, johndoe' })).toBeVisible();
    expect(mainNav()).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Users' })).not.toBeInTheDocument();
  });

  it('shows a User who opens /admin/users Access denied, keeps them signed in and never asks for the list', async () => {
    caller = 'user';
    const router = await open('/admin/users');

    expect(await screen.findByRole('heading', { level: 1, name: 'Access denied' })).toBeVisible();
    expect(screen.getByText("Your account can't open this page. You're still signed in.")).toBeVisible();
    expect(screen.getByRole('button', { name: 'Log out' })).toBeVisible();
    expect(mainNav()).not.toBeInTheDocument();
    expect(userListCalls()).toEqual([]);
    expect(router.state.location.pathname).toBe('/admin/users');

    fireEvent.click(screen.getByRole('link', { name: 'Go to the home page' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Hello, johndoe' })).toBeVisible();
  });

  it("restores an Admin's role on reload of /admin/users: the navigation row and the list come back", async () => {
    caller = 'admin';
    await open('/admin/users');

    expect(await screen.findByRole('table')).toBeVisible();
    expect(within(mainNav()!).getByRole('link', { name: 'Users' })).toHaveAttribute('aria-current', 'page');
    expect(calls.filter((call) => call === 'GET /api/profile')).toHaveLength(1);
  });

  it('shows every field of the table in Singapore time, with "—" and "Never" for missing values', async () => {
    caller = 'admin';
    await open('/admin/users');

    const admin = within(await screen.findByRole('row', { name: /^janeadmin/ }));
    expect(admin.getByText('(you)')).toBeVisible();
    expect(admin.getByText('user0@test.example.com')).toBeVisible();
    expect(admin.getByText('Admin')).toBeVisible();
    expect(admin.getByText('Active')).toBeVisible();
    const [created, lastLogin] = admin.getAllByRole('time');
    expect(created).toHaveAttribute('dateTime', '2026-01-01T00:00:00Z');
    expect(created).toHaveTextContent(/^1 Jan 2026, 8:00\sam$/);
    // ICU versions differ on the September abbreviation ("Sep" or "Sept").
    expect(lastLogin).toHaveTextContent(/^29 Sept? 2026, 9:14\sam$/);
    const neverLoggedIn = within(rowOf('member001'));
    expect(neverLoggedIn.getByText('—')).toBeVisible();
    expect(neverLoggedIn.getByText('Never')).toBeVisible();
    expect(neverLoggedIn.getByText('User')).toBeVisible();
    expect(
      within(table())
        .getAllByRole('columnheader')
        .map((header) => header.textContent),
    ).toEqual(['Username', 'Email', 'Role', 'Status', 'Created (SGT)', 'Last login (SGT)', 'Actions', 'Details']);
  });

  it('shows Access denied when the API answers 403, for an Admin whose role changed since login', async () => {
    caller = 'admin';
    userList = () => ({ status: 403, data: { code: 'ACCESS_DENIED' } });
    await open('/admin/users');

    expect(await screen.findByRole('heading', { level: 1, name: 'Access denied' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Log out' })).toBeVisible();
    expect(userListCalls()).toHaveLength(1);
  });

  it('shows an alert for a server failure, and "Try again" refetches and shows the list', async () => {
    caller = 'admin';
    userList = () => ({ status: 500, data: {} });
    await open('/admin/users');

    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load users. Please try again.');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    userList = (page) => ({ status: 200, data: listPage(page) });

    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByRole('table')).toBeVisible();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('region', { name: 'Users' })).toHaveFocus());
  });

  it('pages with Next and Previous, updating ?page= and disabling at the ends', async () => {
    caller = 'admin';
    const router = await open('/admin/users');
    await screen.findByRole('table');
    const previous = screen.getByRole('button', { name: 'Previous' });
    const next = screen.getByRole('button', { name: 'Next' });
    expect(pageLabel()).toHaveTextContent('Page 1 of 3 · 45 users');
    expect(previous).toBeDisabled();

    next.focus();
    fireEvent.click(next);
    await waitFor(() => expect(pageLabel()).toHaveTextContent('Page 2 of 3 · 45 users'));
    expect(router.state.location.search).toEqual({ page: 2 });
    // The list is not remounted, so the pressed button keeps focus.
    expect(next).toHaveFocus();
    fireEvent.click(next);
    await waitFor(() => expect(pageLabel()).toHaveTextContent('Page 3 of 3 · 45 users'));
    expect(next).toBeDisabled();
    expect(rowOf('member044')).toBeVisible();

    fireEvent.click(previous);
    await waitFor(() => expect(pageLabel()).toHaveTextContent('Page 2 of 3'));
    // Going back shows the cached page at once and refreshes it (TanStack's default staleness).
    expect(userListCalls()).toEqual([
      'GET /api/admin/users?page=0',
      'GET /api/admin/users?page=1',
      'GET /api/admin/users?page=2',
      'GET /api/admin/users?page=1',
    ]);
    expect(pageLabel().closest('[aria-live]')).toHaveAttribute('aria-live', 'polite');
  });

  it('opens ?page=2 as API page 1', async () => {
    caller = 'admin';
    await open('/admin/users?page=2');

    await waitFor(() => expect(pageLabel()).toHaveTextContent('Page 2 of 3'));
    expect(userListCalls()).toEqual(['GET /api/admin/users?page=1']);
  });

  it.each(['?page=0', '?page=-1', '?page=abc', '?page=1.5', ''])(
    'falls back to the first page for %s',
    async (search) => {
      caller = 'admin';
      await open(`/admin/users${search}`);

      await waitFor(() => expect(pageLabel()).toHaveTextContent('Page 1 of 3'));
      expect(userListCalls()).toEqual(['GET /api/admin/users?page=0']);
    },
  );

  it('shows an empty state past the last page, whose "Go to the first page" shows page 1', async () => {
    caller = 'admin';
    const router = await open('/admin/users?page=9');

    expect(await screen.findByText('There are no users on this page.')).toBeVisible();
    fireEvent.click(screen.getByRole('link', { name: 'Go to the first page' }));

    await waitFor(() => expect(pageLabel()).toHaveTextContent('Page 1 of 3'));
    expect(router.state.location.search).toEqual({});
  });

  it('keeps the previous rows, marked busy, while the next page loads, and above them the alert when it fails', async () => {
    caller = 'admin';
    await open('/admin/users');
    await screen.findByRole('table');
    let answer!: (reply: Reply) => void;
    userList = () =>
      new Promise<Reply>((resolve) => {
        answer = resolve;
      });

    fireEvent.click(screen.getByRole('button', { name: 'Next' }));

    await waitFor(() => expect(rowOf('janeadmin').closest('[aria-busy]')).toHaveAttribute('aria-busy', 'true'));
    expect(rowOf('member019')).toBeVisible();
    act(() => {
      answer({ status: 500, data: {} });
    });

    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load users. Please try again.');
    expect(rowOf('janeadmin')).toBeVisible();
    expect(screen.getByRole('alert').compareDocumentPosition(table()) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('drops the User list from the cache on Logout', async () => {
    caller = 'admin';
    const router = await open('/admin/users');
    await screen.findByRole('table');
    expect(router.queryClient.getQueryCache().findAll({ queryKey: ['user-list'] })).not.toHaveLength(0);

    fireEvent.click(screen.getByRole('button', { name: 'Log out' }));

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeVisible();
    expect(router.queryClient.getQueryCache().findAll({ queryKey: ['user-list'] })).toHaveLength(0);
  });
});

describe('the enable/disable control', () => {
  const table = () => screen.getByRole('table');
  const rowOf = (username: string) => within(table()).getByRole('row', { name: new RegExp(`^${username}\\b`) });
  const toggleCalls = () => calls.filter((call) => call.startsWith('PATCH /api/admin/users/'));
  const dialog = () => screen.getByRole('alertdialog');

  it('confirms then disables an enabled Account, updating its row in place from the response', async () => {
    caller = 'admin';
    await open('/admin/users');
    await screen.findByRole('table');
    // member001 is enabled: its control offers Disable.
    fireEvent.click(within(rowOf('member001')).getByRole('button', { name: 'Disable member001' }));

    // A confirmation dialog spells out the consequence; nothing is sent yet.
    expect(dialog()).toHaveAccessibleName('Disable member001?');
    expect(within(dialog()).getByText(/signed out immediately/)).toBeVisible();
    expect(toggleCalls()).toEqual([]);
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Disable' }));

    // The row shows the new state from the response, and the control now offers Enable.
    await waitFor(() => expect(within(rowOf('member001')).getByText('Disabled')).toBeVisible());
    expect(within(rowOf('member001')).getByRole('button', { name: 'Enable member001' })).toBeVisible();
    expect(toggleCalls()).toEqual(['PATCH /api/admin/users/account-1']);
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('confirms both directions and cancelling sends no request', async () => {
    caller = 'admin';
    // member003 starts disabled, so one render has both an enabled row (member002) and a disabled one.
    userList = (page) => ({
      status: 200,
      data: {
        ...listPage(page),
        content: listPage(page).content.map((a) =>
          a.username === 'member003' ? { ...a, enabled: false, disabledAt: '2026-06-01T00:00:00Z' } : a,
        ),
      },
    });
    await open('/admin/users');
    await screen.findByRole('table');

    // Disable direction: cancelling sends nothing and changes nothing.
    fireEvent.click(within(rowOf('member002')).getByRole('button', { name: 'Disable member002' }));
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(within(rowOf('member002')).getByText('Active')).toBeVisible();
    expect(toggleCalls()).toEqual([]);

    // Enable direction: a disabled Account's dialog is primary-toned and states the effect.
    fireEvent.click(within(rowOf('member003')).getByRole('button', { name: 'Enable member003' }));
    expect(dialog()).toHaveAccessibleName('Enable member003?');
    expect(within(dialog()).getByText(/able to log in again/)).toBeVisible();
    expect(within(dialog()).getByRole('button', { name: 'Enable' })).toHaveClass('bg-primary');
  });

  it("shows no control on the caller's own row or a deleted row", async () => {
    caller = 'admin';
    userList = (page) => ({
      status: 200,
      data: {
        ...listPage(page),
        content: listPage(page).content.map((a) =>
          a.username === 'member004'
            ? {
                ...a,
                enabled: false,
                deleted: true,
                disabledAt: '2026-07-01T00:00:00Z',
                deletedAt: '2026-07-01T00:00:00Z',
              }
            : a,
        ),
      },
    });
    await open('/admin/users');
    await screen.findByRole('table');

    // The Admin's own row (janeadmin) shows "Your account", no toggle button.
    expect(within(rowOf('janeadmin')).getByText('Your account')).toBeVisible();
    expect(within(rowOf('janeadmin')).queryByRole('button', { name: /Disable|Enable/ })).not.toBeInTheDocument();
    // A deleted row shows the no-action note.
    expect(within(rowOf('member004')).getByText('No actions — account deleted')).toBeVisible();
    expect(within(rowOf('member004')).queryByRole('button', { name: /Disable|Enable/ })).not.toBeInTheDocument();
  });

  it.each([
    ['ACCOUNT_DELETED', 'This account is deleted and cannot be changed.'],
    ['ADMIN_CANNOT_TARGET_SELF', 'You cannot perform this action on your own account.'],
  ])('shows the server message and leaves the row unchanged on a 409 %s refusal', async (code, detail) => {
    caller = 'admin';
    await open('/admin/users');
    await screen.findByRole('table');
    toggle = () => ({ status: 409, data: { code, detail } });

    fireEvent.click(within(rowOf('member001')).getByRole('button', { name: 'Disable member001' }));
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Disable' }));

    expect(await within(rowOf('member001')).findByRole('alert')).toHaveTextContent(detail);
    expect(within(rowOf('member001')).getByText('Active')).toBeVisible();
    expect(within(rowOf('member001')).getByRole('button', { name: 'Disable member001' })).toBeVisible();
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('shows the concurrency message and refetches the table on a 409 CONCURRENT_MODIFICATION', async () => {
    caller = 'admin';
    await open('/admin/users');
    await screen.findByRole('table');
    const detail = 'This account changed while you were acting on it. Try again.';
    toggle = () => ({ status: 409, data: { code: 'CONCURRENT_MODIFICATION', detail } });
    // Meanwhile another Admin disabled member001: the refetched page shows it.
    userList = (page) => ({
      status: 200,
      data: {
        ...listPage(page),
        content: listPage(page).content.map((a) =>
          a.username === 'member001' ? { ...a, enabled: false, disabledAt: '2026-09-30T00:00:00Z' } : a,
        ),
      },
    });
    const listCalls = () => calls.filter((call) => call.startsWith('GET /api/admin/users')).length;
    const before = listCalls();

    fireEvent.click(within(rowOf('member001')).getByRole('button', { name: 'Disable member001' }));
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Disable' }));

    expect(await within(rowOf('member001')).findByRole('alert')).toHaveTextContent(detail);
    await waitFor(() => expect(within(rowOf('member001')).getByText('Disabled')).toBeVisible());
    expect(listCalls()).toBe(before + 1);
  });

  it('leaves the row unchanged and offers a retry on a 500', async () => {
    caller = 'admin';
    await open('/admin/users');
    await screen.findByRole('table');
    toggle = () => ({ status: 500, data: {} });

    fireEvent.click(within(rowOf('member001')).getByRole('button', { name: 'Disable member001' }));
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Disable' }));

    const row = within(rowOf('member001'));
    expect(await row.findByRole('button', { name: 'Try again' })).toBeVisible();
    expect(row.getByText('Active')).toBeVisible();
    // Retrying with a now-working server updates the row.
    toggle = (id, enabled) => ({ status: 200, data: toggledRow(id, enabled) });
    fireEvent.click(row.getByRole('button', { name: 'Try again' }));
    await waitFor(() => expect(within(rowOf('member001')).getByText('Disabled')).toBeVisible());
  });

  it('marks the control busy while the request is in flight', async () => {
    caller = 'admin';
    await open('/admin/users');
    await screen.findByRole('table');
    let answer!: (reply: Reply) => void;
    toggle = () =>
      new Promise<Reply>((resolve) => {
        answer = resolve;
      });

    fireEvent.click(within(rowOf('member001')).getByRole('button', { name: 'Disable member001' }));
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Disable' }));

    await waitFor(() =>
      expect(within(dialog()).getByRole('button', { name: 'Disabling…' })).toHaveAttribute('aria-busy', 'true'),
    );
    act(() => {
      answer({ status: 200, data: toggledRow('account-1', false) });
    });
    await waitFor(() => expect(within(rowOf('member001')).getByText('Disabled')).toBeVisible());
  });
});

describe('the Delete control', () => {
  const table = () => screen.getByRole('table');
  const rowOf = (username: string) => within(table()).getByRole('row', { name: new RegExp(`^${username}\\b`) });
  const deleteCalls = () => calls.filter((call) => call.startsWith('DELETE /api/admin/users/'));
  const listCalls = () => calls.filter((call) => call.startsWith('GET /api/admin/users')).length;
  const dialog = () => screen.getByRole('alertdialog');
  /** A page where `username` is in the given state; the rest as `listPage`. */
  const withAccount = (username: string, state: Partial<ListedAccount>) => (page: number) => ({
    status: 200,
    data: {
      ...listPage(page),
      content: listPage(page).content.map((a) => (a.username === username ? { ...a, ...state } : a)),
    },
  });
  const tombstone = {
    enabled: false,
    deleted: true,
    disabledAt: '2026-09-30T00:00:00Z',
    deletedAt: '2026-09-30T00:00:00Z',
  };

  it('shows Delete on actionable rows, including disabled and locked ones, and none on your own or a deleted row', async () => {
    caller = 'admin';
    userList = (page) => ({
      status: 200,
      data: {
        ...listPage(page),
        content: listPage(page).content.map((a) => {
          if (a.username === 'member002') return { ...a, enabled: false, disabledAt: '2026-06-01T00:00:00Z' };
          if (a.username === 'member003') return { ...a, locked: true, lockedUntil: '2026-09-30T08:00:00Z' };
          return a.username === 'member004' ? { ...a, ...tombstone } : a;
        }),
      },
    });
    await open('/admin/users');
    await screen.findByRole('table');

    for (const username of ['member001', 'member002', 'member003']) {
      const control = within(rowOf(username)).getByRole('button', { name: `Delete ${username}` });
      expect(control).toBeVisible();
      expect(control).toHaveClass('min-h-11', 'border-danger-line', 'text-danger');
    }
    expect(within(rowOf('janeadmin')).queryByRole('button', { name: /^Delete/ })).not.toBeInTheDocument();
    expect(within(rowOf('janeadmin')).getByText('Your account')).toBeVisible();
    expect(within(rowOf('member004')).queryByRole('button', { name: /^Delete/ })).not.toBeInTheDocument();
    expect(within(rowOf('member004')).getByText('No actions — account deleted')).toBeVisible();
  });

  it('confirms, deletes and refetches the page, after which the row reads Deleted', async () => {
    caller = 'admin';
    await open('/admin/users');
    await screen.findByRole('table');
    const before = listCalls();

    fireEvent.click(within(rowOf('member001')).getByRole('button', { name: 'Delete member001' }));

    expect(dialog()).toHaveAccessibleName('Delete member001?');
    expect(dialog()).toHaveAccessibleDescription(
      'This cannot be undone. They are signed out immediately and can never log in again; their username and email stay reserved.',
    );
    // The safe choice has initial focus; the destructive one is danger-toned.
    expect(within(dialog()).getByRole('button', { name: 'Cancel' })).toHaveFocus();
    expect(within(dialog()).getByRole('button', { name: 'Delete' })).toHaveClass('bg-danger');
    expect(deleteCalls()).toEqual([]);

    userList = withAccount('member001', tombstone);
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(within(rowOf('member001')).getByText('Deleted')).toBeVisible());
    expect(deleteCalls()).toEqual(['DELETE /api/admin/users/account-1']);
    expect(listCalls()).toBe(before + 1);
    expect(within(rowOf('member001')).queryByRole('button', { name: /Delete|Disable|Enable/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('sends nothing and changes nothing when the confirmation is cancelled', async () => {
    caller = 'admin';
    await open('/admin/users');
    await screen.findByRole('table');

    fireEvent.click(within(rowOf('member001')).getByRole('button', { name: 'Delete member001' }));
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(deleteCalls()).toEqual([]);
    expect(within(rowOf('member001')).getByText('Active')).toBeVisible();
  });

  it.each([
    ['ACCOUNT_DELETED', 'This account is deleted and cannot be changed.'],
    ['CONCURRENT_MODIFICATION', 'This account changed while you were acting on it. Try again.'],
  ])('shows the message and refetches the table on a 409 %s', async (code, detail) => {
    caller = 'admin';
    await open('/admin/users');
    await screen.findByRole('table');
    const before = listCalls();
    remove = () => ({ status: 409, data: { code, detail } });
    // Meanwhile someone else deleted it: the refetched page corrects the stale row.
    userList = withAccount('member001', tombstone);

    fireEvent.click(within(rowOf('member001')).getByRole('button', { name: 'Delete member001' }));
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(within(rowOf('member001')).getByText('Deleted')).toBeVisible());
    // The message survives the row turning into a tombstone, which removes its Delete control.
    expect(within(rowOf('member001')).getByRole('alert')).toHaveTextContent(detail);
    expect(within(rowOf('member001')).queryByRole('button', { name: /^Delete/ })).not.toBeInTheDocument();
    expect(listCalls()).toBe(before + 1);
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('shows the message and leaves the row unchanged on a 409 ADMIN_CANNOT_TARGET_SELF', async () => {
    caller = 'admin';
    await open('/admin/users');
    await screen.findByRole('table');
    const before = listCalls();
    const detail = 'You cannot perform this action on your own account.';
    remove = () => ({ status: 409, data: { code: 'ADMIN_CANNOT_TARGET_SELF', detail } });

    fireEvent.click(within(rowOf('member001')).getByRole('button', { name: 'Delete member001' }));
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Delete' }));

    expect(await within(rowOf('member001')).findByRole('alert')).toHaveTextContent(detail);
    expect(within(rowOf('member001')).getByText('Active')).toBeVisible();
    expect(within(rowOf('member001')).getByRole('button', { name: 'Delete member001' })).toBeVisible();
    expect(listCalls()).toBe(before);
  });

  it('renders the server message as text, never as HTML', async () => {
    caller = 'admin';
    await open('/admin/users');
    await screen.findByRole('table');
    remove = () => ({ status: 409, data: { code: 'ACCOUNT_DELETED', detail: '<b>bold</b>' } });

    fireEvent.click(within(rowOf('member001')).getByRole('button', { name: 'Delete member001' }));
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Delete' }));

    const alert = await within(rowOf('member001')).findByRole('alert');
    expect(alert).toHaveTextContent('<b>bold</b>');
    expect(alert.querySelector('b')).toBeNull();
  });

  it('leaves the row unchanged and offers a retry, through the confirmation again, on a 500', async () => {
    caller = 'admin';
    await open('/admin/users');
    await screen.findByRole('table');
    remove = () => ({ status: 500, data: {} });

    fireEvent.click(within(rowOf('member001')).getByRole('button', { name: 'Delete member001' }));
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Delete' }));

    const row = within(rowOf('member001'));
    expect(await row.findByRole('button', { name: 'Try again' })).toBeVisible();
    expect(row.getByText('Active')).toBeVisible();
    remove = () => ({ status: 204 });
    userList = withAccount('member001', tombstone);
    fireEvent.click(row.getByRole('button', { name: 'Try again' }));
    // An irreversible action is never re-sent without a fresh confirmation.
    expect(dialog()).toHaveAccessibleName('Delete member001?');
    expect(deleteCalls()).toHaveLength(1);
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Delete' }));
    await waitFor(() => expect(within(rowOf('member001')).getByText('Deleted')).toBeVisible());
    expect(deleteCalls()).toHaveLength(2);
  });

  it('marks the control busy while the request is in flight', async () => {
    caller = 'admin';
    await open('/admin/users');
    await screen.findByRole('table');
    let answer!: (reply: Reply) => void;
    remove = () =>
      new Promise<Reply>((resolve) => {
        answer = resolve;
      });

    fireEvent.click(within(rowOf('member001')).getByRole('button', { name: 'Delete member001' }));
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Delete' }));

    await waitFor(() =>
      expect(within(dialog()).getByRole('button', { name: 'Deleting…' })).toHaveAttribute('aria-busy', 'true'),
    );
    const control = within(rowOf('member001')).getByRole('button', { name: 'Delete member001' });
    expect(control).toBeDisabled();
    expect(control).toHaveAttribute('aria-busy', 'true');
    expect(control).toHaveTextContent('Deleting…');
    expect(control).toHaveClass('min-w-24');
    userList = withAccount('member001', tombstone);
    act(() => {
      answer({ status: 204 });
    });
    await waitFor(() => expect(within(rowOf('member001')).getByText('Deleted')).toBeVisible());
  });
});

describe('the role-change control', () => {
  const table = () => screen.getByRole('table');
  const cards = () => screen.getAllByRole('listitem');
  const rowOf = (username: string) => within(table()).getByRole('row', { name: new RegExp(`^${username}\\b`) });
  const cardOf = (username: string) =>
    cards().find((card) => within(card).queryByRole('heading', { name: new RegExp(`^${username}\\b`) }))!;
  const roleCalls = () => calls.filter((call) => call.startsWith('PUT /api/admin/users/'));
  const dialog = () => screen.getByRole('alertdialog');
  const pencil = (username: string) =>
    within(rowOf(username)).getByRole('button', { name: `Change role for ${username}` });

  async function openList() {
    caller = 'admin';
    await open('/admin/users');
    await screen.findByRole('table');
  }

  it("shows the pencil on other Accounts' rows and cards, and none on your own or a deleted one", async () => {
    userList = (page) => ({
      status: 200,
      data: {
        ...listPage(page),
        content: listPage(page).content.map((a) =>
          a.username === 'member004' ? { ...a, enabled: false, deleted: true, deletedAt: '2026-07-01T00:00:00Z' } : a,
        ),
      },
    });
    await openList();

    expect(pencil('member001')).toBeVisible();
    expect(within(cardOf('member001')).getByRole('button', { name: 'Change role for member001' })).toBeInTheDocument();
    for (const username of ['janeadmin', 'member004']) {
      expect(within(rowOf(username)).queryByRole('button', { name: /Change role/ })).not.toBeInTheDocument();
      expect(within(cardOf(username)).queryByRole('button', { name: /Change role/ })).not.toBeInTheDocument();
    }
    // The role still shows, with one display rule.
    expect(within(rowOf('janeadmin')).getByText('Admin')).toBeVisible();
    expect(within(rowOf('member004')).getByText('User')).toBeVisible();
  });

  it('lists the roles in a labelled radio group with the current one checked and focused', async () => {
    await openList();
    fireEvent.click(pencil('member001'));

    expect(dialog()).toHaveAccessibleName('Change role for member001');
    const group = await within(dialog()).findByRole('group', { name: 'Role' });
    const user = within(group).getByRole('radio', { name: /User/ });
    expect(user).toBeChecked();
    expect(within(group).getByText('(current)')).toBeVisible();
    expect(user).toHaveFocus();
    expect(within(group).getByRole('radio', { name: 'Admin' })).not.toBeChecked();
    expect(
      within(dialog()).getByText("They'll be signed out and get the new role when they next sign in."),
    ).toBeVisible();
    // Nothing to submit until another role is picked.
    expect(within(dialog()).getByRole('button', { name: 'Change role' })).toBeDisabled();
    fireEvent.click(within(group).getByRole('radio', { name: 'Admin' }));
    expect(within(dialog()).getByRole('button', { name: 'Change role' })).toBeEnabled();
  });

  it('sends nothing when cancelled', async () => {
    await openList();
    fireEvent.click(pencil('member001'));
    fireEvent.click(await within(dialog()).findByRole('radio', { name: 'Admin' }));
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(within(rowOf('member001')).getByText('User')).toBeVisible();
    expect(roleCalls()).toEqual([]);
  });

  it('sends the PUT, shows it is working, then closes, updates the row and announces the change', async () => {
    await openList();
    let answer!: (reply: Reply) => void;
    let sent: string | undefined;
    changeRole = (_id, role) => {
      sent = role;
      return new Promise<Reply>((resolve) => {
        answer = resolve;
      });
    };

    fireEvent.click(pencil('member001'));
    fireEvent.click(await within(dialog()).findByRole('radio', { name: 'Admin' }));
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Change role' }));

    await waitFor(() =>
      expect(within(dialog()).getByRole('button', { name: 'Changing…' })).toHaveAttribute('aria-busy', 'true'),
    );
    expect(within(dialog()).getByRole('radio', { name: 'Admin' })).toBeDisabled();
    expect(within(dialog()).getByRole('button', { name: 'Cancel' })).toBeDisabled();
    expect(roleCalls()).toEqual(['PUT /api/admin/users/account-1/role']);
    expect(sent).toBe('ADMIN');

    act(() => {
      answer({ status: 200, data: { ...toggledRow('account-1', true), role: 'ADMIN' } });
    });
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(within(rowOf('member001')).getByText('Admin')).toBeVisible();
    expect(await screen.findByText('member001 is now Admin. They have been signed out.')).toHaveAttribute(
      'role',
      'status',
    );
  });

  it.each([
    [409, 'ACCOUNT_DELETED', 'This account is deleted and cannot be changed.'],
    [400, 'INVALID_REQUEST', 'Request validation failed.'],
  ])('shows the server message in the popup and leaves the row unchanged on a %i', async (status, code, detail) => {
    await openList();
    changeRole = () => ({ status, data: { code, detail } });

    fireEvent.click(pencil('member001'));
    fireEvent.click(await within(dialog()).findByRole('radio', { name: 'Admin' }));
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Change role' }));

    expect(await within(dialog()).findByRole('alert')).toHaveTextContent(detail);
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Cancel' }));
    expect(within(rowOf('member001')).getByText('User')).toBeVisible();
  });

  it('offers a retry in the popup on a 500, and retrying resends', async () => {
    await openList();
    changeRole = () => ({ status: 500, data: {} });

    fireEvent.click(pencil('member001'));
    fireEvent.click(await within(dialog()).findByRole('radio', { name: 'Admin' }));
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Change role' }));

    expect(await within(dialog()).findByRole('alert')).toHaveTextContent('Something went wrong.');
    changeRole = (id, role) => ({ status: 200, data: { ...toggledRow(id, true), role } });
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Try again' }));
    await waitFor(() => expect(within(rowOf('member001')).getByText('Admin')).toBeVisible());
    expect(roleCalls()).toHaveLength(2);
  });

  it('offers a retry where the radios would be when the roles fail to load', async () => {
    await openList();
    roles = () => ({ status: 500, data: {} });

    fireEvent.click(pencil('member001'));

    expect(await within(dialog()).findByRole('alert')).toHaveTextContent('Something went wrong.');
    expect(within(dialog()).queryByRole('radio')).not.toBeInTheDocument();
    roles = () => ({ status: 200, data: ['USER', 'ADMIN'] });
    fireEvent.click(within(dialog()).getByRole('button', { name: 'Try again' }));
    expect(await within(dialog()).findByRole('radio', { name: 'Admin' })).toBeVisible();
  });

  it('shows an undeclared role by its raw name in the list and the popup', async () => {
    roles = () => ({ status: 200, data: ['USER', 'ADMIN', 'AUDITOR'] });
    userList = (page) => ({
      status: 200,
      data: {
        ...listPage(page),
        content: listPage(page).content.map((a) => (a.username === 'member002' ? { ...a, role: 'AUDITOR' } : a)),
      },
    });
    await openList();

    expect(within(rowOf('member002')).getByText('AUDITOR')).toBeVisible();
    fireEvent.click(pencil('member002'));
    expect(await within(dialog()).findByRole('radio', { name: /AUDITOR/ })).toBeChecked();
  });
});
