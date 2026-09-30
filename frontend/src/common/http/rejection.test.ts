import { describe, expect, it } from 'vitest';
import { axiosFailure } from '../../test-support';
import { Rejection, toRejection } from './rejection';

const classifyNothing = () => undefined;

describe('toRejection', () => {
  it.each([
    ['a 429', axiosFailure(429), 'too-many-attempts'],
    ['a 500', axiosFailure(500), 'unavailable'],
    ['a network failure', axiosFailure(), 'unavailable'],
    ['a contract violation', new Error('unexpected body'), 'unavailable'],
  ])('maps %s by the global rules', (_name, error, kind) => {
    expect(toRejection(error, classifyNothing).kind).toBe(kind);
  });

  it('leaves every other status to the classifier, and falls back to unavailable', () => {
    const classify = ({ status, code }: { status: number; code?: string }) =>
      status === 400 && code === 'SOMETHING' ? 'something' : undefined;

    expect(toRejection(axiosFailure(400, { code: 'SOMETHING' }), classify).kind).toBe('something');
    expect(toRejection(axiosFailure(400, { code: 'OTHER' }), classify).kind).toBe('unavailable');
    expect(toRejection(axiosFailure(429, { code: 'SOMETHING' }), classify).kind).toBe('too-many-attempts');
  });

  it('keeps only the field and code of each field-error entry, and tolerates a malformed body', () => {
    const classify = () => 'rejected' as const;
    const rejection = toRejection(
      axiosFailure(400, { errors: [{ field: 'username', code: 'USERNAME_TOO_SHORT', value: 'x' }] }),
      classify,
    );

    expect(rejection.kind).toBe('rejected');
    expect(rejection.errors).toEqual([{ field: 'username', code: 'USERNAME_TOO_SHORT' }]);
    expect(toRejection(axiosFailure(400, '<html>'), classify).errors).toEqual([]);
  });

  it('passes an existing rejection through', () => {
    const rejection = new Rejection('too-many-attempts');
    expect(toRejection(rejection, classifyNothing)).toBe(rejection);
  });
});
