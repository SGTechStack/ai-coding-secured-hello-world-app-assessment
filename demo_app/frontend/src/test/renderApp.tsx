import { QueryClient } from "@tanstack/react-query";
import { createMemoryHistory } from "@tanstack/react-router";
import { render } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App } from "../App";
import { createAppRouter } from "../router";

/**
 * Renders the real app (router + query client) at `initialPath`, with the network mocked by MSW.
 * Assert on what a user sees; use the returned `router` only to read the current URL.
 */
export function renderApp(initialPath = "/") {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  const history = createMemoryHistory({ initialEntries: [initialPath] });
  const router = createAppRouter({ queryClient, history });
  const user = userEvent.setup();

  render(<App router={router} queryClient={queryClient} />);

  return { user, router, queryClient };
}
