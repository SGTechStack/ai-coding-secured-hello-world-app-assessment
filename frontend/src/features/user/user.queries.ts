import { queryOptions, useSuspenseQuery } from '@tanstack/react-query';
import {
  getCurrentUserProfile,
  getGetCurrentUserProfileQueryKey,
} from '@/api/generated/current-user-profile-controller/current-user-profile-controller';
import type { UserProfileResponse } from '@/api/generated/openAPIDefinition.schemas';

export type User = {
  username: string;
  displayName: string;
  roles: string[];
};

export const currentUserQueryOptions = () =>
  queryOptions({
    queryKey: getGetCurrentUserProfileQueryKey(),
    queryFn: async () => {
      const res = await getCurrentUserProfile();
      const profile = res.data as UserProfileResponse;
      return {
        username: profile.username ?? '',
        displayName: profile.displayName ?? '',
        roles: profile.roles ?? [],
      } satisfies User;
    },
    staleTime: 5 * 60 * 1000,
  });

export const useCurrentUser = () => useSuspenseQuery(currentUserQueryOptions());

export function getInitials(displayName: string): string {
  return displayName
    .split(' ')
    .map((w) => w[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
}
