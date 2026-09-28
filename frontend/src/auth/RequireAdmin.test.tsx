import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import type { AuthenticatedUser } from '../api/authApi';
import RequireAdmin from './RequireAdmin';

function renderWithUser(user: AuthenticatedUser) {
  return render(
    <MemoryRouter initialEntries={['/admin']}>
      <Routes>
        <Route
          path="/admin"
          element={
            <RequireAdmin user={user}>
              <div>Admin content</div>
            </RequireAdmin>
          }
        />
        <Route path="/" element={<div>Landing page stub</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('RequireAdmin', () => {
  it('renders the guarded content for an ADMIN user', () => {
    renderWithUser({ username: 'johndoe', email: 'johndoe@example.com', role: 'ADMIN' });

    expect(screen.getByText('Admin content')).toBeInTheDocument();
  });

  it('redirects a non-admin user to /', () => {
    renderWithUser({ username: 'johndoe', email: 'johndoe@example.com', role: 'USER' });

    expect(screen.getByText('Landing page stub')).toBeInTheDocument();
    expect(screen.queryByText('Admin content')).not.toBeInTheDocument();
  });
});
