import { describe, it, expect, vi, beforeEach } from 'vitest';
import { orvalFetch } from './openapi-orval-mutator';

vi.mock('@lib/http', () => ({
  http: vi.fn(),
  HttpError: class HttpError extends Error {
    status: number;
    body: unknown;
    constructor(status: number, body?: unknown) {
      super(`HTTP ${status}`);
      this.status = status;
      this.body = body;
    }
  },
}));

import { http } from '@lib/http';
const mockHttp = vi.mocked(http);

function makeResponse(status: number, body?: unknown, ok = true): Response {
  return {
    status,
    ok,
    json: body !== undefined ? () => Promise.resolve(body) : () => Promise.reject(new Error('no body')),
    headers: new Headers(),
  } as unknown as Response;
}

describe('orvalFetch', () => {
  beforeEach(() => {
    mockHttp.mockReset();
  });

  it('returns { data, status, headers } on success', async () => {
    const payload = { id: 1, name: 'test' };
    mockHttp.mockResolvedValue(makeResponse(200, payload));

    const result = await orvalFetch<{ data: typeof payload; status: number }>('/api/items', {});

    expect(result).toMatchObject({ data: payload, status: 200 });
  });

  it('returns { data: undefined } for 204 No Content', async () => {
    mockHttp.mockResolvedValue(makeResponse(204, undefined, true));

    const result = await orvalFetch<{ data: undefined; status: number }>('/api/items/1', {
      method: 'DELETE',
    });

    expect((result as { data: undefined }).data).toBeUndefined();
  });

  it('throws HttpError with status and parsed body on non-ok response', async () => {
    const problem = { title: 'Not Found', status: 404 };
    mockHttp.mockResolvedValue(makeResponse(404, problem, false));

    await expect(orvalFetch('/api/missing', {})).rejects.toMatchObject({
      status: 404,
      body: problem,
    });
  });

  it('throws HttpError with undefined body when error response is not JSON', async () => {
    mockHttp.mockResolvedValue({
      status: 500,
      ok: false,
      json: () => Promise.reject(new SyntaxError('invalid json')),
      headers: new Headers(),
    } as unknown as Response);

    await expect(orvalFetch('/api/crash', {})).rejects.toMatchObject({
      status: 500,
      body: undefined,
    });
  });

  it('passes url and options through to http()', async () => {
    mockHttp.mockResolvedValue(makeResponse(200, {}));

    await orvalFetch('/api/test', { method: 'POST', body: '{}' });

    expect(mockHttp).toHaveBeenCalledWith('/api/test', { method: 'POST', body: '{}' });
  });
});
