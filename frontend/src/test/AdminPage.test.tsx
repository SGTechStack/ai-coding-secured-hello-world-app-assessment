import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../contexts/AuthContext';
import { AdminPage } from '../pages/AdminPage';
import { mockAdmin, mockProblemDetail, mockResponse, mockUser, setCsrfCookie } from './helpers';

const targetUser = mockUser({ id: '00000000-0000-0000-0000-000000000002', username: 'target' });
const adminUser = mockAdmin({ id: '00000000-0000-0000-0000-000000000001' });

function renderAdminPage(meUser = adminUser) {
  vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
    const path = typeof url === 'string' ? url : url.toString();
    if (path.includes('/api/auth/csrf')) return Promise.resolve(mockResponse({ token: 'x' }));
    if (path.includes('/api/auth/me')) return Promise.resolve(mockResponse(meUser));
    if (path.endsWith('/api/admin/users')) return Promise.resolve(mockResponse([adminUser, targetUser]));
    return Promise.reject(new Error('unexpected: ' + path));
  });

  return render(
    <MemoryRouter>
      <AuthProvider>
        <AdminPage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('AdminPage', () => {
  beforeEach(() => { setCsrfCookie(); });

  it('renders user table for ADMIN role', async () => {
    renderAdminPage(adminUser);
    expect(await screen.findByText('target')).toBeInTheDocument();
    expect(screen.getByText('admin')).toBeInTheDocument();
  });

  it('hides action buttons for own row (self-action protection in UI)', async () => {
    renderAdminPage(adminUser);
    await screen.findByText('target');
    // Admin's own row should NOT have action buttons
    const rows = screen.getAllByRole('row');
    const adminRow = rows.find((r) => r.textContent?.includes('admin'));
    expect(adminRow).toBeDefined();
    // The admin row should not have a disable/delete button
    expect(adminRow?.querySelector('button')).toBeNull();
  });

  it('does not display passwordHash in any rendered output', async () => {
    renderAdminPage(adminUser);
    await screen.findByText('target');
    expect(document.body.textContent).not.toMatch(/\$2a\$|passwordHash/);
  });

  it('calls PATCH /status with CSRF header on disable', async () => {
    // Set up mock BEFORE rendering (renderAdminPage would override the spy).
    setCsrfCookie('admin-csrf');
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
      const path = typeof url === 'string' ? url : url.toString();
      if (path.includes('/api/auth/csrf')) return Promise.resolve(mockResponse({ token: 'x' }));
      if (path.includes('/api/auth/me')) return Promise.resolve(mockResponse(adminUser));
      if (path.endsWith('/api/admin/users')) return Promise.resolve(mockResponse([adminUser, targetUser]));
      if (path.includes('/status')) return Promise.resolve(mockResponse({ ...targetUser, enabled: false }));
      return Promise.reject(new Error('unexpected: ' + path));
    });

    render(
      <MemoryRouter>
        <AuthProvider>
          <AdminPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    const disableBtn = await screen.findByRole('button', { name: /disable target/i });
    await userEvent.click(disableBtn);

    await waitFor(() => {
      const statusCall = fetchSpy.mock.calls.find(([u]) =>
        typeof u === 'string' && u.includes('/status'),
      );
      expect(statusCall).toBeDefined();
      const [, init] = statusCall as [string, RequestInit];
      const headers = init.headers as Record<string, string>;
      expect(headers['X-XSRF-TOKEN']).toBe('admin-csrf');
    });
  });

  it('shows 403 error message when listing users returns 403', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((url: RequestInfo | URL) => {
      const path = typeof url === 'string' ? url : url.toString();
      if (path.includes('/api/auth/csrf')) return Promise.resolve(mockResponse({ token: 'x' }));
      if (path.includes('/api/auth/me')) return Promise.resolve(mockResponse(adminUser));
      if (path.endsWith('/api/admin/users')) return Promise.resolve(mockProblemDetail(403, 'Forbidden'));
      return Promise.reject(new Error('unexpected: ' + path));
    });

    render(
      <MemoryRouter>
        <AuthProvider>
          <AdminPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
