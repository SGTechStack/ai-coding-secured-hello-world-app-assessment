import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import type { UserListPage } from '../model/user-list';
import { userListQueryOptions } from '../queries/user-list';

/**
 * One page of the User list. The composing page passes the caller's id in, so this feature never imports `auth`.
 *
 * `shown` is the page to display: the requested one once loaded, else the last one that loaded, so a failed page
 * change keeps the previous rows on screen with the error above them.
 * @param page 0-based
 */
export function useUserList(userId: string, page: number) {
  const query = useQuery(userListQueryOptions(userId, page));
  const [last, setLast] = useState<UserListPage>();
  if (query.data && query.data !== last) setLast(query.data);
  return { query, shown: query.data ?? last, loadingPage: query.isPlaceholderData };
}
