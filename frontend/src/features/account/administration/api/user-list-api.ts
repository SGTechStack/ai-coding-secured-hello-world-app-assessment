import axios from 'axios';
import { apiClient } from '../../../../common/http/api-client';
import { problemDetailSchema, type ProblemDetailJson } from '../../../../common/http/problem-detail';
import { userListPageSchema, type UserListPage } from '../model/user-list';

/** One page of the User list. @param page 0-based. Failures propagate raw. */
export async function fetchUserList(page: number): Promise<UserListPage> {
  return userListPageSchema.parse((await apiClient.get<UserListPage>('/api/admin/users', { params: { page } })).data);
}

/** The server refused the caller's role (403 ACCESS_DENIED), e.g. after a role change since login. */
export const isAccessDenied = (error: unknown) =>
  axios.isAxiosError<ProblemDetailJson>(error) &&
  problemDetailSchema.safeParse(error.response?.data).data?.code === 'ACCESS_DENIED';
