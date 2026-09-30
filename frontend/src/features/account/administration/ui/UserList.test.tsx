import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { axeViolations } from '../../../../test-support';
import type { ListedAccount, UserListPage } from '../model/user-list';
import { UserList } from './UserList';

const account = (overrides: Partial<ListedAccount> & Pick<ListedAccount, 'id' | 'username'>): ListedAccount => ({
  email: `${overrides.username}@test.example.com`,
  role: 'USER',
  enabled: true,
  deleted: false,
  locked: false,
  failedLoginAttempts: 0,
  lockedUntil: null,
  disabledAt: null,
  deletedAt: null,
  lastLoginAt: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-02-01T04:30:00Z',
  ...overrides,
});

const ACCOUNTS: ListedAccount[] = [
  account({ id: 'id-admin', username: 'janeadmin', role: 'ADMIN', lastLoginAt: '2026-09-29T01:14:00Z' }),
  account({
    id: 'id-locked',
    username: 'locked001',
    locked: true,
    failedLoginAttempts: 5,
    lockedUntil: '2026-09-29T02:00:00Z',
  }),
  account({ id: 'id-idle', username: 'idle00001', email: null, enabled: false, disabledAt: '2026-06-01T00:00:00Z' }),
  account({
    id: 'id-gone',
    username: 'gone00001',
    enabled: false,
    deleted: true,
    disabledAt: '2026-07-01T00:00:00Z',
    deletedAt: '2026-07-01T00:00:00Z',
  }),
];
const page = (number: number): UserListPage => ({
  content: ACCOUNTS,
  page: { size: 20, number, totalElements: 44, totalPages: 3 },
});

afterEach(cleanup);

function renderList(number = 0) {
  const props = { callerId: 'id-admin', busy: false, onPageChange: vi.fn(), onToggled: vi.fn() };
  const client = new QueryClient();
  // The role-change popup's roles, as `GET /api/admin/roles` would answer (this test has no server).
  client.setQueryData(['admin-roles'], ['USER', 'ADMIN']);
  const wrap = (page: UserListPage) => (
    <QueryClientProvider client={client}>
      <UserList shown={page} {...props} />
    </QueryClientProvider>
  );
  const view = render(wrap(page(number)));
  return { ...view, rerenderAt: (next: number) => view.rerender(wrap(page(next))) };
}

const table = () => screen.getByRole('table');
const cards = () => screen.getByRole('list');
const card = (username: string) =>
  within(cards())
    .getByRole('heading', { level: 2, name: new RegExp(`^${username}`) })
    .closest('li')!;

describe('UserList', () => {
  it('shows every primary field in the table, with status and role as text, and marks the caller', () => {
    renderList();
    const rows = within(table()).getAllByRole('row').slice(1);

    expect(rows.map((row) => within(row).getAllByRole('cell')[3].textContent)).toEqual([
      'Active',
      'Locked',
      'Disabled',
      'Deleted',
    ]);
    const admin = within(rows[0]);
    expect(admin.getByText('(you)')).toBeVisible();
    expect(admin.getByText('Admin')).toBeVisible();
    expect(admin.getByText('janeadmin@test.example.com')).toBeVisible();
    expect(within(rows[1]).queryByText('(you)')).not.toBeInTheDocument();
    expect(within(rows[1]).getByText('Never')).toBeVisible();
    expect(within(rows[2]).getByText('—')).toBeVisible();
    expect(within(table()).getByText('Users, page 1 of 3')).toHaveClass('sr-only');
  });

  it('shows every primary field on the cards, with the username as a heading and the status beside it', () => {
    renderList();
    const gone = within(card('gone00001'));

    expect(gone.getByText('Deleted')).toBeVisible();
    expect(gone.getByText('gone00001@test.example.com')).toBeVisible();
    expect(gone.getByText('User')).toBeVisible();
    expect(gone.getByText('Created (SGT)')).toBeVisible();
    expect(gone.getByText('Never')).toBeVisible();
    expect(within(card('janeadmin')).getByText('(you)')).toBeVisible();
    expect(within(card('idle00001')).getByText('Email').nextSibling).toHaveTextContent('—');
  });

  it('reveals the detail fields from a row, announced by aria-expanded and aria-controls, keeping focus', () => {
    renderList();
    const button = within(table()).getByRole('button', { name: 'Show details for locked001' });
    expect(button).toHaveAttribute('aria-expanded', 'false');
    expect(button).toHaveClass('size-11');

    button.focus();
    fireEvent.click(button);

    expect(button).toHaveAttribute('aria-expanded', 'true');
    expect(button).toHaveAccessibleName('Hide details for locked001');
    expect(button).toHaveFocus();
    const details = within(document.getElementById(button.getAttribute('aria-controls')!)!);
    expect(details.getByText('id-locked')).toHaveClass('font-mono');
    expect(details.getByText('Failed login attempts').nextSibling).toHaveTextContent('5');
    expect(details.getByText('Locked until (SGT)').nextSibling).toHaveTextContent(/^29 Sept? 2026, 10:00\sam$/);
    for (const label of ['Disabled at (SGT)', 'Deleted at (SGT)'])
      expect(details.getByText(label).nextSibling).toHaveTextContent('—');
    expect(details.getByText('Updated at (SGT)').nextSibling).toHaveTextContent(/^1 Feb 2026, 12:30\spm$/);
  });

  it('reveals the same detail fields from a card', () => {
    renderList();
    const button = within(card('gone00001')).getByRole('button', { name: 'Show details for gone00001' });

    fireEvent.click(button);

    expect(button).toHaveAttribute('aria-expanded', 'true');
    expect(button).toHaveTextContent('Hide details');
    const details = within(document.getElementById(button.getAttribute('aria-controls')!)!);
    expect(details.getByText('Deleted at (SGT)').nextSibling).toHaveTextContent(/^1 Jul 2026, 8:00\sam$/);
    expect(details.getByText('Account ID').nextSibling).toHaveTextContent('id-gone');
  });

  it('keeps several rows open at once, and closes them all when the page changes', () => {
    const { rerenderAt } = renderList();
    fireEvent.click(within(table()).getByRole('button', { name: 'Show details for janeadmin' }));
    fireEvent.click(within(table()).getByRole('button', { name: 'Show details for idle00001' }));

    expect(within(table()).getAllByRole('button', { name: /^Hide details/ })).toHaveLength(2);
    rerenderAt(1);
    expect(within(table()).queryAllByRole('button', { name: /^Hide details/ })).toHaveLength(0);
  });

  it('shows a compact enable/disable control per actionable row, none on the own or deleted row', () => {
    renderList();
    const rows = within(table()).getAllByRole('row').slice(1);
    // Row 0 is the caller (janeadmin): a note, no control.
    expect(within(rows[0]).getByText('Your account')).toBeVisible();
    expect(within(rows[0]).queryByRole('button', { name: /Disable|Enable/ })).not.toBeInTheDocument();
    // Row 1 (locked001) is enabled: Disable, danger-tinted and compact.
    const disable = within(rows[1]).getByRole('button', { name: 'Disable locked001' });
    expect(disable).toHaveClass('text-danger', 'text-xs', 'min-h-11');
    // Row 2 (idle00001) is disabled: Enable.
    expect(within(rows[2]).getByRole('button', { name: 'Enable idle00001' })).toBeVisible();
    // Row 3 (gone00001) is deleted: the no-action note, no control.
    expect(within(rows[3]).getByText('No actions — account deleted')).toBeVisible();
    expect(within(rows[3]).queryByRole('button', { name: /Disable|Enable/ })).not.toBeInTheDocument();
  });

  it('has no axe violations, with details open in both variants', async () => {
    const { container } = renderList();
    fireEvent.click(within(table()).getByRole('button', { name: 'Show details for janeadmin' }));
    fireEvent.click(within(card('locked001')).getByRole('button', { name: 'Show details for locked001' }));

    expect(await axeViolations(container)).toEqual([]);
  });

  it('has no axe violations with a confirmation dialog open', async () => {
    const { container } = renderList();
    fireEvent.click(within(table()).getByRole('button', { name: 'Disable locked001' }));

    expect(screen.getByRole('alertdialog', { name: 'Disable locked001?' })).toBeVisible();
    expect(await axeViolations(container)).toEqual([]);
  });

  it.each(['table', 'card'])('has no axe violations with the role-change popup open from the %s', async (from) => {
    const { container } = renderList();
    const scope = from === 'table' ? within(table()) : within(card('locked001'));
    fireEvent.click(scope.getByRole('button', { name: 'Change role for locked001' }));

    expect(screen.getByRole('alertdialog', { name: 'Change role for locked001' })).toBeVisible();
    expect(screen.getByRole('radio', { name: /User/ })).toBeChecked();
    expect(await axeViolations(container)).toEqual([]);
  });
});
