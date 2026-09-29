import { apiJson } from './client';

export async function getHello(): Promise<string> {
  const data = await apiJson<{ message: string }>('/api/hello');
  return data.message;
}
