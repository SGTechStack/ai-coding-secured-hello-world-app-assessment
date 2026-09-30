import { createFileRoute, ErrorComponent, redirect } from '@tanstack/react-router';
import { readSession, restoreSession, SessionRestoreError } from '../features/auth/session';
import { AuthenticatedLayout } from '../pages/authenticated/AuthenticatedLayout';
import { SessionRestoreFailed } from '../pages/authenticated/SessionRestoreFailed';
import { SessionPlaceholder } from '../pages/authenticated/SessionRestoring';

export const Route = createFileRoute('/_authenticated')({
  // Synchronous while the Session is in memory, so in-app navigation never shows the placeholder. After a reload it
  // is empty: ask the server whether the Session is still live (ADR 0005).
  beforeLoad: ({ context }) => {
    if (readSession(context.queryClient)) return;
    return restoreSession(context.queryClient).then((restored) => {
      if (!restored) throw redirect({ to: '/login' });
    });
  },
  pendingMs: 0,
  pendingComponent: () => <SessionPlaceholder busy />,
  errorComponent: ({ error }) =>
    error instanceof SessionRestoreError ? <SessionRestoreFailed /> : <ErrorComponent error={error} />,
  component: AuthenticatedLayout,
});
