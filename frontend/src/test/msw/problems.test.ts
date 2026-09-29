import { Ajv2020 } from 'ajv/dist/2020.js'
import { describe, expect, it } from 'vitest'
import schema from '../../../../docs/api/error-contract.schema.json'
import { ERROR_CODES, type Problem } from '@/lib/api/errors'
import { problemFixtures, problemResponse } from './problems'

// The schema the backend generates from its ErrorCode enum and drift-checks in `mvn verify` (R-AUTH-003).
const validate = new Ajv2020({ allErrors: true, strict: true }).compile(schema)

describe('error contract', () => {
  it.each(Object.entries(problemFixtures))(
    'T-AUTH-012: the %s fixture validates against the generated schema',
    (_code, fixture) => {
      expect(validate(fixture), JSON.stringify(validate.errors)).toBe(true)
    },
  )

  it('T-AUTH-012: a problemResponse body validates against the generated schema', async () => {
    const body: unknown = await problemResponse('ACCESS_DENIED', '/api/admin/users').json()

    expect(validate(body), JSON.stringify(validate.errors)).toBe(true)
  })

  it('T-AUTH-012: an unknown code fails the schema', () => {
    expect(validate({ ...problemFixtures.ACCESS_DENIED, code: 'NOT_A_CODE' })).toBe(false)
  })

  it.each(Object.keys(problemFixtures.INTERNAL_ERROR))(
    'T-AUTH-012: a fixture missing %s fails the schema',
    (member) => {
      const incomplete: Partial<Problem> = { ...problemFixtures.INTERNAL_ERROR }
      delete incomplete[member]

      expect(validate(incomplete)).toBe(false)
    },
  )

  it('T-AUTH-012: PASSWORD_REJECTED needs its rule, and no other code may carry one', () => {
    const { rule: _rule, ...withoutRule } = problemFixtures.PASSWORD_REJECTED

    expect(validate(withoutRule)).toBe(false)
    expect(validate({ ...problemFixtures.PASSWORD_REJECTED, rule: 'NOT_A_RULE' })).toBe(false)
    expect(validate({ ...problemFixtures.VALIDATION_FAILED, rule: 'TOO_WEAK' })).toBe(false)
  })

  it('T-AUTH-012: a code paired with another status fails the schema', () => {
    expect(validate({ ...problemFixtures.ACCESS_DENIED, status: 401 })).toBe(false)
  })

  it('the SPA code list is exactly the schema enum, and every code has a fixture', () => {
    expect([...ERROR_CODES]).toEqual(schema.properties.code.enum)
    expect(Object.keys(problemFixtures).sort()).toEqual([...ERROR_CODES].sort())
  })
})
