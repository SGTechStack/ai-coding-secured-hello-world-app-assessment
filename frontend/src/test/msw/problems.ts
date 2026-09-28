import { HttpResponse } from 'msw'
import type { ErrorCode, Problem } from '@/lib/api/errors'

/**
 * Every MSW error fixture, one per code, exactly as the backend writes it. T-AUTH-012 validates each against the
 * JSON Schema generated from the backend's enum, so a fixture cannot invent a code or drop a member.
 * Build error responses in handlers with {@link problemResponse}, never by hand.
 */
export const problemFixtures: Readonly<Record<ErrorCode, Problem>> = {
  AUTHENTICATION_FAILED: fixture(
    'AUTHENTICATION_FAILED',
    401,
    'Authentication failed',
    'Authentication is required, or the credentials were not accepted.',
  ),
  PASSWORD_CHANGE_REQUIRED: fixture(
    'PASSWORD_CHANGE_REQUIRED',
    403,
    'Password change required',
    'The password must be changed before this request can be made.',
  ),
  CSRF_TOKEN_INVALID: fixture(
    'CSRF_TOKEN_INVALID',
    403,
    'CSRF token invalid',
    'The CSRF token is missing, invalid or no longer current.',
  ),
  ACCESS_DENIED: fixture('ACCESS_DENIED', 403, 'Access denied', 'The request is not permitted.'),
  VALIDATION_FAILED: fixture('VALIDATION_FAILED', 400, 'Validation failed', 'The request was not valid.'),
  PASSWORD_REJECTED: fixture(
    'PASSWORD_REJECTED',
    400,
    'Password rejected',
    'The password does not meet the password policy.',
  ),
  RESET_TOKEN_INVALID: fixture('RESET_TOKEN_INVALID', 400, 'Token invalid', 'The token is invalid or has expired.'),
  USER_EXISTS: fixture('USER_EXISTS', 400, 'User exists', 'A user with that username or email address already exists.'),
  TOO_MANY_REQUESTS: fixture('TOO_MANY_REQUESTS', 429, 'Too many requests', 'Too many requests. Try again later.'),
  MISSING_FACTOR: fixture(
    'MISSING_FACTOR',
    412,
    'Second factor required',
    'A current second-factor verification is required.',
  ),
  INVALID_FACTOR: fixture('INVALID_FACTOR', 412, 'Second factor invalid', 'The second-factor code was not accepted.'),
  FACTOR_ENROLMENT_REQUIRED: fixture(
    'FACTOR_ENROLMENT_REQUIRED',
    422,
    'Second factor enrolment required',
    'A second factor must be enrolled before this request can be made.',
  ),
  FACTOR_ALREADY_ENROLLED: fixture(
    'FACTOR_ALREADY_ENROLLED',
    409,
    'Second factor already enrolled',
    'A second factor is already enrolled.',
  ),
  FACTOR_DISABLED: fixture(
    'FACTOR_DISABLED',
    423,
    'Second factor disabled',
    'The second factor is disabled. Contact an administrator.',
  ),
  INTERNAL_ERROR: fixture('INTERNAL_ERROR', 500, 'Internal error', 'An unexpected error occurred.'),
}

/** An MSW response carrying the fixture for `code`, as the backend sends it. */
export function problemResponse(code: ErrorCode, instance = '/api'): HttpResponse<Problem> {
  const body = { ...problemFixtures[code], instance }
  return HttpResponse.json(body, { status: body.status, headers: { 'Content-Type': 'application/problem+json' } })
}

function fixture(code: ErrorCode, status: number, title: string, detail: string): Problem {
  return {
    type: `tag:securedhello.sg,2026:problem:${code.toLowerCase().replaceAll('_', '-')}`,
    title,
    status,
    detail,
    instance: '/api',
    traceId: '0123456789abcdef0123456789abcdef',
    code,
  }
}
