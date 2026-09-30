import { keepPreviousData, queryOptions } from '@tanstack/react-query';
import { fetchUserList } from '../api/user-list-api';
import { PAGE_SIZE } from '../model/user-list';

/** The prefix of every User list page's key, e.g. to refetch the shown page after an admin action. */
export const USER_LIST_KEY = ['user-list'] as const;

/**
 * A private per-Admin query, keyed by the caller's id. Ending the Session clears the whole cache, so the next person
 * at this computer never sees it. The previous page stays on screen while the next loads. No automatic retry: the
 * page offers "Try again", and a 401 or 403 is never retried.
 * @param page 0-based
 */
export function userListQueryOptions(userId: string, page: number) {
  return queryOptions({
    queryKey: [...USER_LIST_KEY, userId, page, PAGE_SIZE],
    queryFn: () => fetchUserList(page),
    placeholderData: keepPreviousData,
    retry: false,
  });
}
