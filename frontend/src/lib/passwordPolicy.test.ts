import { describe, expect, it } from 'vitest';
import { passwordProblems } from './passwordPolicy';

describe('passwordProblems', () => {
  it('accepts twelve characters', () => {
    expect(passwordProblems('abcdefghijkl')).toEqual([]);
  });

  it('rejects eleven characters', () => {
    expect(passwordProblems('abcdefghijk')).toEqual(['Must be at least 12 characters long']);
  });

  it('rejects over 72 characters', () => {
    expect(passwordProblems('x'.repeat(73))).toEqual(['Must be at most 72 characters long']);
  });

  it('rejects passwords containing the username', () => {
    expect(passwordProblems('Alice-is-great-2026', 'alice')).toEqual(['Must not contain the username']);
    expect(passwordProblems('Alice-is-great-2026', '')).toEqual([]);
  });
});
