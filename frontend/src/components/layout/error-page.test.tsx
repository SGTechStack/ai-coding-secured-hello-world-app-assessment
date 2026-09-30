import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider, createRouter, createMemoryHistory, createRootRoute } from '@tanstack/react-router';
import { ErrorPage } from './error-page';

function renderErrorPage(reset = vi.fn()) {
  const rootRoute = createRootRoute({ component: () => <ErrorPage reset={reset} /> });
  const router = createRouter({
    routeTree: rootRoute,
    history: createMemoryHistory({ initialEntries: ['/some-error-path'] }),
  });
  render(<RouterProvider router={router} />);
  return { reset, router };
}

describe('ErrorPage', () => {
  it('renders the error heading', async () => {
    renderErrorPage();
    await waitFor(() => expect(screen.getByText('Something went wrong')).toBeInTheDocument());
  });

  it('"Try again" calls reset', async () => {
    const user = userEvent.setup();
    const reset = vi.fn();
    renderErrorPage(reset);
    await waitFor(() => screen.getByRole('button', { name: 'Try again' }));
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(reset).toHaveBeenCalledOnce();
  });

  it('"Go home" does a full-page navigation to /', async () => {
    const user = userEvent.setup();
    const hrefSetter = vi.fn();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: {
        set href(value: string) {
          hrefSetter(value);
        },
      },
    });

    renderErrorPage();
    await waitFor(() => screen.getByRole('button', { name: 'Go home' }));
    await user.click(screen.getByRole('button', { name: 'Go home' }));

    expect(hrefSetter).toHaveBeenCalledWith('/');
  });
});
