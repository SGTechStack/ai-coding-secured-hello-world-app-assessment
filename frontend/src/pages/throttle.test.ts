import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/client';
import { throttledMessage } from './throttle';

const throttled = (retryAfterSeconds?: number) =>
  new ApiError(
    retryAfterSeconds === undefined
      ? { status: 429, code: 'too many requests' }
      : { status: 429, code: 'too many requests', retryAfterSeconds },
  );

describe('throttledMessage', () => {
  it('tells the user how many seconds to wait', () => {
    expect(throttledMessage(throttled(30))).toBe('Too many attempts. Try again in 30 seconds.');
  });

  it('says "second" for a one-second wait', () => {
    expect(throttledMessage(throttled(1))).toBe('Too many attempts. Try again in 1 second.');
  });

  it('falls back to "later" when the wait is unknown', () => {
    expect(throttledMessage(throttled())).toBe('Too many attempts. Please try again later.');
  });

  it('has nothing to say about any other error', () => {
    expect(throttledMessage(new ApiError({ status: 401, code: 'invalid credentials' }))).toBeUndefined();
    expect(throttledMessage(new Error('network down'))).toBeUndefined();
  });
});
