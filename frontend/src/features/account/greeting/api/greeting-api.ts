import { z } from 'zod';
import { apiClient } from '../../../../common/http/api-client';

const greetingSchema = z.object({ message: z.string() });
export type Greeting = z.infer<typeof greetingSchema>;

/** The Greeting the server confirms for the caller's live Session. Failures (including 401) propagate raw. */
export async function fetchGreeting(): Promise<Greeting> {
  return greetingSchema.parse((await apiClient.get<Greeting>('/api/hello')).data);
}
