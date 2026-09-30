import { createFileRoute } from '@tanstack/react-router';
import { ResetPasswordPage } from '../pages/password-reset/ResetPasswordPage';

export const Route = createFileRoute('/reset-password')({ component: ResetPasswordPage });
