import { http, HttpError } from '@lib/http';
import type { ProblemDetails } from '@lib/http';

export async function orvalFetch<T>(url: string, options: RequestInit): Promise<T> {
  const res = await http(url, options);
  if (!res.ok) {
    const body = (await res.json().catch(() => undefined)) as ProblemDetails | undefined;
    throw new HttpError(res.status, body);
  }
  const data = res.status === 204 ? undefined : await res.json();
  return { data, status: res.status, headers: res.headers } as unknown as T;
}
