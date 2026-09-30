import { apiClient } from '../../../../common/http/api-client';
import { profileSchema, type Profile } from '../model/profile';

/** The caller's own profile; a 401 means there is no live Session. Failures propagate raw. */
export async function fetchProfile(): Promise<Profile> {
  return profileSchema.parse((await apiClient.get<Profile>('/api/profile')).data);
}
