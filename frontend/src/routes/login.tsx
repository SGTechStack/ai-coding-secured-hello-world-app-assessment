import { createFileRoute } from '@tanstack/react-router';
import { z } from 'zod';
import { LoginPage } from '../pages/login/LoginPage';

// `reset=done` is set by a completed Password reset, so the page can confirm it; anything else is ignored.
const loginSearchSchema = z.object({ reset: z.literal('done').optional().catch(undefined) });

export const Route = createFileRoute('/login')({ component: LoginPage, validateSearch: loginSearchSchema });
