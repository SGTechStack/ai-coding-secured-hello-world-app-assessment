import { describe, expect, it } from 'vitest'
import { passwordProblem } from './passwordPolicy'

describe('passwordProblem', () => {
  it('requires at least 12 characters', () => {
    expect(passwordProblem('elevenchars')).toMatch(/at least 12/)
    expect(passwordProblem('twelve chars')).toBeNull()
  })

  it('counts characters, not UTF-16 code units', () => {
    expect(passwordProblem('🔒'.repeat(11))).toMatch(/at least 12/)
  })

  it('rejects passwords over 72 bytes, which BCrypt would truncate', () => {
    expect(passwordProblem('€'.repeat(24))).toBeNull()
    expect(passwordProblem('€'.repeat(24) + 'x')).toMatch(/too long/)
  })
})
