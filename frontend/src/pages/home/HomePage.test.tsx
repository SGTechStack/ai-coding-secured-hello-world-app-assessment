import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, createEvent, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { Greeting } from '../../features/account/greeting/api/greeting-api';
import { axiosFailure } from '../../test-support';

const get = vi.fn<(url: string) => Promise<{ data: Greeting }>>();
vi.mock('../../common/http/api-client', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  apiClient: { get: (url: string) => get(url) },
}));
vi.mock('../../features/auth/session', () => ({ readSession: () => ({ id: 'user-1', username: 'johndoe' }) }));

import { HomePage } from './HomePage';

const TITLE = 'Unable to load your greeting';
const MESSAGE = 'Unable to load your greeting. Please try again.';

let queryClient: QueryClient;
beforeEach(() => {
  // The page relies on the greeting query's own retry policy, so the client keeps TanStack's defaults.
  queryClient = new QueryClient();
  get.mockReset();
});
afterEach(cleanup);

function renderPage() {
  render(
    <QueryClientProvider client={queryClient}>
      <HomePage />
    </QueryClientProvider>,
  );
}

const serverError = () => new Error('server error');
const authenticationRequired = () => axiosFailure(401, { code: 'AUTHENTICATION_REQUIRED' });

async function failOnce() {
  get.mockRejectedValueOnce(serverError());
  renderPage();
  return screen.findByRole('alertdialog', { name: TITLE });
}

const pressEsc = (dialog: HTMLElement) => fireEvent(dialog, createEvent('cancel', dialog, { cancelable: true }));
const inlineRetry = () => screen.getByRole('button', { name: 'Try again' });

describe('HomePage', () => {
  it('holds the heading place with a busy placeholder while the Greeting loads', () => {
    get.mockReturnValue(new Promise(() => {}));
    renderPage();

    const placeholder = screen.getByRole('status');
    expect(placeholder).toHaveAttribute('aria-busy', 'true');
    expect(placeholder).toHaveAccessibleName('Loading your greeting');
    // Same height as the heading's line box (text-2xl = 2rem = h-8), so the heading replaces it without a shift.
    expect(placeholder).toHaveClass('h-8');
    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
  });

  it('shows the server Greeting as the page heading', async () => {
    get.mockResolvedValue({ data: { message: 'Hello, johndoe' } });
    renderPage();

    const heading = await screen.findByRole('heading', { level: 1, name: 'Hello, johndoe' });
    expect(heading).toHaveClass('text-2xl');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(get).toHaveBeenCalledWith('/api/hello');
  });

  it('caches the Greeting under the signed-in User', async () => {
    get.mockResolvedValue({ data: { message: 'Hello, johndoe' } });
    renderPage();

    await screen.findByRole('heading', { name: 'Hello, johndoe' });
    expect(queryClient.getQueryData(['greeting', 'user-1'])).toEqual({ message: 'Hello, johndoe' });
  });

  it('opens an error dialog with its message and both actions when the Greeting fails', async () => {
    const dialog = await failOnce();

    expect(get).toHaveBeenCalledOnce();
    expect(dialog).toHaveAccessibleDescription(MESSAGE);
    expect(screen.getByRole('button', { name: 'Try again' })).toHaveFocus();
    expect(screen.getByRole('button', { name: 'Close' })).toBeVisible();
  });

  it('"Try again" closes the dialog and shows the Greeting when the retry succeeds', async () => {
    await failOnce();
    get.mockResolvedValueOnce({ data: { message: 'Hello, johndoe' } });

    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Hello, johndoe' })).toBeVisible();
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(get).toHaveBeenCalledTimes(2);
  });

  it('"Try again" reopens the dialog when the retry fails too', async () => {
    await failOnce();
    let fail!: (error: Error) => void;
    get.mockReturnValueOnce(
      new Promise((_, reject) => {
        fail = reject;
      }),
    );

    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(screen.getByRole('status', { name: 'Loading your greeting' })).toBeVisible();

    fail(serverError());
    expect(await screen.findByRole('alertdialog', { name: TITLE })).toBeVisible();
    expect(get).toHaveBeenCalledTimes(2);
  });

  it.each([
    ['"Close"', () => fireEvent.click(screen.getByRole('button', { name: 'Close' }))],
    ['Esc', () => pressEsc(screen.getByRole('alertdialog'))],
  ])('%s leaves a Welcome heading with an inline "Try again" that has focus', async (_, dismiss) => {
    await failOnce();

    dismiss();

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Welcome' })).toBeVisible();
    expect(inlineRetry()).toHaveFocus();
    expect(get).toHaveBeenCalledOnce();
  });

  it('the inline "Try again" refetches: a success shows the Greeting', async () => {
    await failOnce();
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    get.mockResolvedValueOnce({ data: { message: 'Hello, johndoe' } });

    fireEvent.click(inlineRetry());

    expect(await screen.findByRole('heading', { level: 1, name: 'Hello, johndoe' })).toBeVisible();
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it.each([
    ['the dialog', () => fireEvent.click(screen.getByRole('button', { name: 'Try again' }))],
    [
      'the inline button',
      () => {
        fireEvent.click(screen.getByRole('button', { name: 'Close' }));
        fireEvent.click(inlineRetry());
      },
    ],
  ])('a successful "Try again" from %s moves focus to the Greeting, not the page body', async (_, retry) => {
    await failOnce();
    get.mockResolvedValueOnce({ data: { message: 'Hello, johndoe' } });

    retry();

    await waitFor(() => expect(screen.getByRole('heading', { level: 1, name: 'Hello, johndoe' })).toHaveFocus());
  });

  it('opens the dialog for a 401 that is not Authentication-required, so the User is never stuck loading', async () => {
    get.mockRejectedValueOnce(axiosFailure(401, {}));
    renderPage();

    expect(await screen.findByRole('alertdialog', { name: TITLE })).toBeVisible();
  });

  it('the inline "Try again" reopens the dialog when the retry fails', async () => {
    await failOnce();
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    get.mockRejectedValueOnce(serverError());

    fireEvent.click(inlineRetry());

    expect(await screen.findByRole('alertdialog', { name: TITLE })).toBeVisible();
    expect(get).toHaveBeenCalledTimes(2);
  });

  it('never opens the dialog, nor retries, for a 401: the global Session-expired handling owns it', async () => {
    get.mockRejectedValue(authenticationRequired());
    renderPage();

    await waitFor(() => expect(queryClient.getQueryState(['greeting', 'user-1'])?.status).toBe('error'));
    expect(get).toHaveBeenCalledOnce();
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
