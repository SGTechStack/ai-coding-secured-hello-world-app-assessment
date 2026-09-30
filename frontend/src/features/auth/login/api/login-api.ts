import { apiClient } from '../../../../common/http/api-client';
import { loginResponseSchema, type LoginInput, type LoginResponse } from '../model/login';

// Failures, including a body that breaks the contract, propagate raw; the login form maps them with toLoginRejection.
export async function login(input: LoginInput): Promise<LoginResponse> {
  return loginResponseSchema.parse((await apiClient.post<LoginResponse>('/api/auth/login', input)).data);
}
