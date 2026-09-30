import { createRouter } from '@tanstack/react-router';
import { onSessionExpired } from '../common/http/api-client';
import { endSession } from '../features/auth/session';
import { routeTree } from '../routeTree.gen';
import { queryClient } from './query-client';

export const router = createRouter({ routeTree, context: { queryClient } });

// Session expired ends the Session exactly like Logout does.
onSessionExpired(() => void endSession(queryClient, router));

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router;
  }
}
