import { buildRequirements, validatePassword } from '../utils/passwordPolicy';

/**
 * Frontend password policy tests (mirror of backend PasswordPolicyTest).
 * The backend is authoritative; these verify the UX logic is consistent.
 */
describe('validatePassword', () => {
  // ── length ──────────────────────────────────────────────────────────────

  it('rejects 11-char password', () => {
    expect(validatePassword('Abc-1234567')).toMatch(/at least 12/i);
  });

  it('accepts 12-char password with 3 categories', () => {
    expect(validatePassword('abcABC123456')).toBeNull();
  });

  it('accepts 72-char password with 3 categories', () => {
    const pw = 'a1-' + 'a'.repeat(69); // 72 chars
    expect(pw).toHaveLength(72);
    expect(validatePassword(pw)).toBeNull();
  });

  it('rejects 73-char password', () => {
    const pw = 'a1-' + 'a'.repeat(70); // 73 chars
    expect(pw).toHaveLength(73);
    expect(validatePassword(pw)).toMatch(/not exceed 72/i);
  });

  // ── complexity ────────────────────────────────────────────────────────

  it('rejects lowercase-only password', () => {
    expect(validatePassword('alllowercaseee')).toMatch(/3 of the following/i);
  });

  it('rejects lowercase + uppercase only (2 categories)', () => {
    expect(validatePassword('AllLowerUpper')).toMatch(/3 of the following/i);
  });

  it('rejects lowercase + digit only (2 categories)', () => {
    expect(validatePassword('lowercase12345')).toMatch(/3 of the following/i);
  });

  it('accepts lowercase + uppercase + digit (3 categories)', () => {
    expect(validatePassword('LowerUpper1234')).toBeNull();
  });

  it('accepts lowercase + digit + special (3 categories)', () => {
    expect(validatePassword('lowercase123!!!')).toBeNull();
  });

  it('accepts uppercase + digit + special (3 categories)', () => {
    expect(validatePassword('UPPERCASE123!!!')).toBeNull();
  });

  it('accepts all four categories', () => {
    expect(validatePassword('LowerUpper123!!!')).toBeNull();
  });

  // ── existing test passwords still accepted ────────────────────────────

  it.each([
    'secure-pass-12',
    'brand-new-pass-34',
    'boot-admin-pass-99',
    'a-strong-prod-password-99',
  ])('accepts existing test password: %s', (pw) => {
    expect(validatePassword(pw)).toBeNull();
  });

  // ── error message must not contain the submitted password ─────────────

  it('error message does not echo the submitted password', () => {
    const pw = 'weakpassword';
    const err = validatePassword(pw);
    expect(err).not.toBeNull();
    expect(err).not.toContain(pw);
  });
});

describe('buildRequirements', () => {
  it('shows all 7 requirement items', () => {
    const reqs = buildRequirements('');
    expect(reqs).toHaveLength(7);
  });

  it('marks length requirement as met when 12+ chars', () => {
    const reqs = buildRequirements('a'.repeat(12));
    const lengthReq = reqs.find((r) => r.label.includes('At least 12'));
    expect(lengthReq?.met).toBe(true);
  });

  it('marks max-length requirement correctly', () => {
    const empty = buildRequirements('').find((r) => r.label.includes('No more than 72'));
    const normal = buildRequirements('abc').find((r) => r.label.includes('No more than 72'));
    const over = buildRequirements('a'.repeat(73)).find((r) => r.label.includes('No more than 72'));
    expect(empty?.met).toBe(false);  // empty: length > 0 guard fails
    expect(normal?.met).toBe(true);  // 3 chars ≤ 72
    expect(over?.met).toBe(false);   // 73 chars > 72
  });

  it('marks uppercase requirement when present', () => {
    const reqs = buildRequirements('ABC123');
    expect(reqs.find((r) => r.label.includes('Uppercase'))?.met).toBe(true);
  });

  it('marks lowercase requirement when present', () => {
    const reqs = buildRequirements('abc123');
    expect(reqs.find((r) => r.label.includes('Lowercase'))?.met).toBe(true);
  });

  it('marks digit requirement when present', () => {
    const reqs = buildRequirements('abc123');
    expect(reqs.find((r) => r.label.includes('Number'))?.met).toBe(true);
  });

  it('marks special-character requirement when present', () => {
    const reqs = buildRequirements('abc!');
    expect(reqs.find((r) => r.label.includes('Special'))?.met).toBe(true);
  });

  it('marks category-count requirement when 3+ categories present', () => {
    // 3 categories: lower + digit + special
    const reqs = buildRequirements('abc123!');
    expect(reqs.find((r) => r.label.includes('3 character'))?.met).toBe(true);
  });

  it('marks category-count requirement as unmet when only 2 categories', () => {
    // 2 categories: lower + digit only
    const reqs = buildRequirements('abcdef123');
    expect(reqs.find((r) => r.label.includes('3 character'))?.met).toBe(false);
  });
});
