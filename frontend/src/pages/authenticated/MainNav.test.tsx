import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from '@tanstack/react-router';
import { act, cleanup, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { axeViolations } from '../../test-support';
import { MainNav } from './MainNav';

afterEach(cleanup);

/** The row inside a minimal router at `path`, so its links know which item is current. */
async function renderAt(path: string, role: string | undefined) {
  const root = createRootRoute({ component: () => <MainNav role={role} /> });
  const routes = ['/home', '/admin/users'].map((to) => createRoute({ getParentRoute: () => root, path: to }));
  const router = createRouter({
    routeTree: root.addChildren(routes),
    history: createMemoryHistory({ initialEntries: [path] }),
  });
  let container!: HTMLElement;
  await act(async () => {
    ({ container } = render(<RouterProvider router={router} />));
    await router.load();
  });
  return container;
}

describe('MainNav', () => {
  it('lists Home and Users for an Admin, marking the current item', async () => {
    await renderAt('/admin/users', 'ADMIN');
    const nav = within(screen.getByRole('navigation', { name: 'Main' }));

    expect(nav.getAllByRole('link').map((link) => link.textContent)).toEqual(['Home', 'Users']);
    expect(nav.getByRole('link', { name: 'Users' })).toHaveAttribute('aria-current', 'page');
    expect(nav.getByRole('link', { name: 'Users' })).toHaveClass('font-semibold', 'text-primary');
    expect(nav.getByRole('link', { name: 'Home' })).not.toHaveAttribute('aria-current');
    expect(nav.getByRole('link', { name: 'Home' })).toHaveClass('min-h-11', 'text-ink-muted');
  });

  it.each([['USER'], [undefined]])('renders no row when %s has a single destination', async (role) => {
    await renderAt('/home', role);

    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('has no axe violations', async () => {
    const container = await renderAt('/home', 'ADMIN');

    expect(await axeViolations(container)).toEqual([]);
  });
});
