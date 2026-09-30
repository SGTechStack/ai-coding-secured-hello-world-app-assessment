import { createFileRoute } from '@tanstack/react-router';
import { ForgotPasswordPage } from '../pages/password-reset/ForgotPasswordPage';

export const Route = createFileRoute('/forgot-password')({ component: ForgotPasswordPage });
