import { describe, expect, it } from 'vitest';
import { cases } from '../../../../../../test-fixtures/password-policy-cases.json';
import { emailViolations, fieldMessageFor, passwordViolations, usernameViolations } from './account-rules';

describe('password policy shared with the backend', () => {
  it.each(cases)('$name', ({ password, username, email, codes }) => {
    const byCode = (a: string, b: string) => a.localeCompare(b);
    expect(passwordViolations(password, username, email).sort(byCode)).toEqual([...codes].sort(byCode));
  });
});

describe('identifier rules', () => {
  it.each([
    ['', ['FIELD_REQUIRED']],
    ['abcd', ['USERNAME_TOO_SHORT']],
    ['a'.repeat(101), ['USERNAME_TOO_LONG']],
    ['.testuser', ['USERNAME_INVALID_CHARACTER']],
    ['test user', ['USERNAME_INVALID_CHARACTER']],
    ['  TestUser_1.a-b ', []],
  ])('username %j', (username, codes) => {
    expect(usernameViolations(username)).toEqual(codes);
  });

  it.each([
    ['', ['FIELD_REQUIRED']],
    ['not-an-email', ['EMAIL_INVALID']],
    ['a@b@test.example.com', ['EMAIL_INVALID']],
    [`testuser@${'a'.repeat(60).concat('.').repeat(4)}example.com`, ['EMAIL_TOO_LONG']],
    [' TestUser123@Test.Example.com ', []],
  ])('email %j', (email, codes) => {
    expect(emailViolations(email)).toEqual(codes);
  });
});

describe('code-to-message mapping', () => {
  it.each([
    'USERNAME_TOO_SHORT',
    'USERNAME_TOO_LONG',
    'USERNAME_INVALID_CHARACTER',
    'EMAIL_INVALID',
    'EMAIL_TOO_LONG',
    'PASSWORD_TOO_SHORT',
    'PASSWORD_TOO_LONG',
    'PASSWORD_INVALID_CHARACTER',
    'PASSWORD_MISSING_UPPERCASE',
    'PASSWORD_MISSING_LOWERCASE',
    'PASSWORD_MISSING_DIGIT',
    'PASSWORD_MISSING_SPECIAL',
    'PASSWORD_CONTAINS_IDENTITY',
    'PASSWORD_TOO_COMMON',
  ])('maps %s to its own fixed message', (code) => {
    expect(fieldMessageFor('password', code)).toMatch(/\S/);
  });

  it('maps FIELD_REQUIRED per field and leaves unknown codes unmapped', () => {
    expect(fieldMessageFor('email', 'FIELD_REQUIRED')).toBe('Email is required');
    expect(fieldMessageFor('username', 'SOMETHING_NEW')).toBeUndefined();
  });
});
