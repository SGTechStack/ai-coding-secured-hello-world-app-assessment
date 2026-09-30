import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { UserSummary } from '../../api/types';
import { UserTable } from './UserTable';

const users: UserSummary[] = [
  {
    id: '1',
    username: 'root',
    email: 'root@example.com',
    role: 'ADMIN',
    enabled: true,
    createdAt: '2026-01-01T00:00:00Z',
  },
  {
    id: '2',
    username: 'bob',
    email: 'bob@example.com',
    role: 'USER',
    enabled: false,
    createdAt: '2026-01-02T00:00:00Z',
  },
];

describe('UserTable', () => {
  it('disables every action on the current admin row and marks it', () => {
    render(
      <UserTable
        users={users}
        currentUsername="root"
        busy={false}
        onToggleEnabled={vi.fn()}
        onChangeRole={vi.fn()}
        onDelete={vi.fn()}
      />,
    );

    expect(screen.getByText('(you)')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Disable root' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Delete root' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Enable bob' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Delete bob' })).toBeEnabled();
  });

  it('reports actions on other users', async () => {
    const onToggleEnabled = vi.fn();
    const onChangeRole = vi.fn();
    const onDelete = vi.fn();
    render(
      <UserTable
        users={users}
        currentUsername="root"
        busy={false}
        onToggleEnabled={onToggleEnabled}
        onChangeRole={onChangeRole}
        onDelete={onDelete}
      />,
    );
    const user = userEvent.setup();

    await user.click(screen.getByRole('button', { name: 'Enable bob' }));
    await user.click(screen.getByRole('button', { name: 'Delete bob' }));
    await user.selectOptions(screen.getByRole('combobox', { name: 'Role for bob' }), 'ADMIN');

    expect(onToggleEnabled).toHaveBeenCalledWith(users[1]);
    expect(onDelete).toHaveBeenCalledWith(users[1]);
    expect(onChangeRole).toHaveBeenCalledWith(users[1], 'ADMIN');
  });

  it('shows an empty state', () => {
    render(
      <UserTable
        users={[]}
        currentUsername="root"
        busy={false}
        onToggleEnabled={vi.fn()}
        onChangeRole={vi.fn()}
        onDelete={vi.fn()}
      />,
    );
    expect(screen.getByText('No users yet.')).toBeInTheDocument();
  });
});
