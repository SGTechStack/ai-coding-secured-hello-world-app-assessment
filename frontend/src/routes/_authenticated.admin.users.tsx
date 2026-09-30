import { createFileRoute } from '@tanstack/react-router';
import { z } from 'zod';
import { UserListPage } from '../pages/user-list/UserListPage';

// 1-based for people; a missing, zero, negative or non-numeric page means the first one.
const searchSchema = z.object({ page: z.coerce.number().int().min(1).optional().catch(undefined) });

export const Route = createFileRoute('/_authenticated/admin/users')({
  validateSearch: searchSchema,
  component: UserListPage,
});
