import { describe, it, expect } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider, createRouter, createMemoryHistory, createRootRoute } from '@tanstack/react-router';
import { NotFoundPage } from './not-found-page';

function renderNotFoundPage() {
  const rootRoute = createRootRoute({ component: () => <NotFoundPage /> });
  const router = createRouter({
    routeTree: rootRoute,
    history: createMemoryHistory({ initialEntries: ['/missing'] }),
  });
  render(<RouterProvider router={router} />);
  return { router };
}

describe('NotFoundPage', () => {
  it('renders the not found heading', async () => {
    renderNotFoundPage();
    await waitFor(() => expect(screen.getByText('Page not found')).toBeInTheDocument());
  });

  it('"Go home" navigates to /', async () => {
    const user = userEvent.setup();
    const { router } = renderNotFoundPage();
    await waitFor(() => screen.getByRole('button', { name: 'Go home' }));
    await user.click(screen.getByRole('button', { name: 'Go home' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
  });
});
