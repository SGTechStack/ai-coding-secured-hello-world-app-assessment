// Shared test fixtures and fakes (test files only; outside the layered tree, so the boundaries test ignores it).
import { AxiosError, AxiosHeaders, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';
import { vi } from 'vitest';
import type { CsrfResponse } from './common/http/csrf';
import type { ProblemDetailJson } from './common/http/problem-detail';
import type { FieldErrorsProblemJson } from './common/http/rejection';
import type { LoginResponse } from './features/auth/login/model/login';

/** The illustrative User's profile, as login returns it. */
export const JOHNDOE: LoginResponse['profile'] = {
  id: '49ac0d5f-9634-49a3-8d46-45a6d19c3fc8',
  username: 'johndoe',
  role: 'USER',
};

/** axe-core rule ids the rendered `node` violates. Colour contrast is left out: jsdom does no layout or painting. */
export async function axeViolations(node: Element): Promise<string[]> {
  const { default: axe } = await import('axe-core');
  const result = await axe.run(node, { rules: { 'color-contrast': { enabled: false } } });
  return result.violations.map((violation) => `${violation.id}: ${violation.nodes.map((n) => n.html).join(' | ')}`);
}

/** A `GET /csrf` body carrying `token` for the default header. */
export const csrfToken = (token = 'session-token'): CsrfResponse => ({
  token,
  headerName: 'X-CSRF-TOKEN',
  parameterName: '_csrf',
});

/**
 * Error bodies a test may feed: Problem Details, a rejected registration, entries carrying a member the client must
 * drop, or a non-JSON page (e.g. from a proxy).
 */
type FailureJson =
  ProblemDetailJson | FieldErrorsProblemJson | { errors: { field: string; code: string; value: string }[] } | '<html>';

function failure<T>(config: InternalAxiosRequestConfig, status?: number, data?: T): AxiosError<T | undefined> {
  if (status === undefined) return new AxiosError('Network Error', 'ERR_NETWORK', config);
  return new AxiosError('refused', 'ERR_BAD_REQUEST', config, undefined, {
    config,
    data,
    headers: new AxiosHeaders(),
    status,
    statusText: '',
  });
}

/** An Axios failure with a response of `status` and body `data`, or a network failure (no response) without a status. */
export const axiosFailure = (status?: number, data?: FailureJson) =>
  failure({ headers: new AxiosHeaders() }, status, data);

/** A fake server's answer: a status (>= 400 rejects, as Axios does) with an optional body, or a network failure. */
export type FakeReply<T> = { status: number; data?: T } | 'network-error';

/** A fake Axios adapter behind the real transport: `reply` answers each request (throw to fail the test request). */
export function fakeAdapter<T>(reply: (config: InternalAxiosRequestConfig) => FakeReply<T> | Promise<FakeReply<T>>) {
  return vi.fn(async (config: InternalAxiosRequestConfig): Promise<AxiosResponse<T | undefined>> => {
    const answer = await reply(config);
    if (answer === 'network-error') throw failure(config);
    if (answer.status >= 400) throw failure(config, answer.status, answer.data);
    return { config, data: answer.data, headers: new AxiosHeaders(), status: answer.status, statusText: '' };
  });
}
