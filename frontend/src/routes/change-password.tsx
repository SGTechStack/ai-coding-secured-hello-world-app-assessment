import { createFileRoute } from '@tanstack/react-router';
import { ChangePasswordPage } from '@features/auth/change-password-page';

// Outside `_authenticated` on purpose: that layout probes /api/v1/me, which the server refuses
// with 403 until the password is changed. A visitor with no session gets a 401 on submit.
export const Route = createFileRoute('/change-password')({
  component: ChangePasswordPage,
});
