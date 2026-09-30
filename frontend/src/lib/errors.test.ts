import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/client';
import { describeError, fieldErrorsOf } from './errors';

describe('describeError', () => {
  it('uses the problem detail for client errors', () => {
    const error = new ApiError(401, { status: 401, detail: 'Invalid username or password' });
    expect(describeError(error)).toBe('Invalid username or password');
  });

  it('hides server internals for 5xx', () => {
    const error = new ApiError(500, { status: 500, detail: 'NullPointerException at ...' });
    expect(describeError(error)).toBe('The server had a problem. Please try again in a moment.');
  });

  it('explains network failures', () => {
    expect(describeError(new TypeError('Failed to fetch'))).toBe(
      'Could not reach the server. Is the backend running?',
    );
  });

  it('falls back for unknown values', () => {
    expect(describeError(42)).toBe('Something went wrong.');
  });
});

describe('fieldErrorsOf', () => {
  it('extracts field errors from ApiError and nothing from other errors', () => {
    const error = new ApiError(400, {
      status: 400,
      errors: [
        { field: 'password', message: 'too short' },
        { field: 'password', message: 'second message ignored' },
      ],
    });
    expect(fieldErrorsOf(error)).toEqual({ password: 'too short' });
    expect(fieldErrorsOf(new Error('x'))).toEqual({});
  });
});
