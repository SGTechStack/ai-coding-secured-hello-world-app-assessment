import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import {
  evaluate,
  findFeatureFiles,
  findReferences,
  levelOf,
  parseFeature,
  qualifierOf,
} from './traceability.mjs'

const FEATURE = `@auth
Feature: Login
  As a user

  @story1-ac1
  Scenario: First
    Given something

  @story1-ac2 @wip
  Scenario: Second
    Given something else
`

describe('parseFeature', () => {
  it('reads the title and tagged scenarios in order', () => {
    const { title, scenarios, errors } = parseFeature(FEATURE, 'f')
    assert.equal(title, 'Login')
    assert.deepEqual(scenarios, [
      { id: 'story1-ac1', name: 'First', wip: false },
      { id: 'story1-ac2', name: 'Second', wip: true },
    ])
    assert.deepEqual(errors, [])
  })

  it('rejects a scenario without an ID tag', () => {
    const { errors } = parseFeature('Feature: X\n  Scenario: Untagged\n', 'f')
    assert.match(errors[0], /needs exactly one/)
  })

  it('rejects an ID that does not match the scenario position', () => {
    const { errors } = parseFeature('Feature: X\n  @story1-ac2\n  Scenario: A\n', 'f')
    assert.match(errors[0], /is #1 but tagged @story1-ac2/)
  })

  it('rejects mixed story prefixes in one file', () => {
    const text = 'Feature: X\n  @story1-ac1\n  Scenario: A\n  @story2-ac2\n  Scenario: B\n'
    assert.ok(parseFeature(text, 'f').errors.some((e) => /mixes story prefixes/.test(e)))
  })

  it('qualifies IDs of a feature in a subdirectory without changing its tags', () => {
    const { scenarios, errors } = parseFeature(
      FEATURE,
      'stories/assessment/story1.feature',
      'assessment',
    )
    assert.deepEqual(
      scenarios.map((s) => s.id),
      ['assessment/story1-ac1', 'assessment/story1-ac2'],
    )
    assert.deepEqual(errors, [])
  })

  it('applies the position and prefix rules per file when qualified', () => {
    const text = 'Feature: X\n  @story1-ac2\n  Scenario: A\n  @story2-ac2\n  Scenario: B\n'
    const { errors } = parseFeature(text, 'f', 'assessment')
    assert.match(errors[0], /is #1 but tagged @story1-ac2/)
    assert.ok(errors.some((e) => /mixes story prefixes story1, story2/.test(e)))
  })
})

describe('qualifierOf', () => {
  it('is empty for root features and the relative directory otherwise', () => {
    assert.equal(qualifierOf('story1.feature'), '')
    assert.equal(qualifierOf('assessment/story1.feature'), 'assessment')
    assert.equal(qualifierOf('a\\b\\story1.feature'), 'a/b')
  })
})

describe('findFeatureFiles', () => {
  it('finds feature files recursively, sorted, skipping generated directories', () => {
    const root = mkdtempSync(join(tmpdir(), 'trace-'))
    try {
      mkdirSync(join(root, 'assessment', 'deep'), { recursive: true })
      mkdirSync(join(root, 'node_modules'))
      writeFileSync(join(root, 'story1.feature'), '')
      writeFileSync(join(root, 'notes.md'), '')
      writeFileSync(join(root, 'assessment', 'story2.feature'), '')
      writeFileSync(join(root, 'assessment', 'deep', 'story3.feature'), '')
      writeFileSync(join(root, 'node_modules', 'story4.feature'), '')
      assert.deepEqual(findFeatureFiles(root), [
        'assessment/deep/story3.feature',
        'assessment/story2.feature',
        'story1.feature',
      ])
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
  })
})

describe('findReferences', () => {
  it('collects bracketed IDs from test names', () => {
    const ids = findReferences("it('[story1-ac1][story1-ac2] works')")
    assert.deepEqual([...ids], ['story1-ac1', 'story1-ac2'])
  })

  it('ignores unbracketed labels', () => {
    assert.equal(findReferences('Feature: Login AC1: story1-ac1').size, 0)
  })

  it('collects qualified IDs alongside unqualified ones', () => {
    const ids = findReferences(
      '@DisplayName("[assessment/story1-ac1][story1-ac2][a/b/story3-ac4] works")',
    )
    assert.deepEqual([...ids], ['assessment/story1-ac1', 'story1-ac2', 'a/b/story3-ac4'])
  })
})

describe('levelOf', () => {
  it('splits backend tests into unit and integration', () => {
    assert.equal(levelOf('backend', 'a/LoginControllerIT.java'), 'backend-it')
    assert.equal(levelOf('backend', 'a/LoginServiceTest.java'), 'backend-unit')
    assert.equal(levelOf('frontend', 'a.test.ts'), 'frontend')
  })
})

describe('evaluate', () => {
  const features = [parseFeature(FEATURE, 'f')]

  it('fails an untested scenario that is not @wip', () => {
    const { rows, errors } = evaluate(features, [])
    assert.equal(rows[0].status, 'MISSING')
    assert.equal(rows[1].status, 'wip')
    assert.deepEqual(errors, ['@story1-ac1 "First" has no test'])
  })

  it('reports covering levels and warns when a covered scenario is still @wip', () => {
    const refs = [
      {
        file: 'a',
        level: 'frontend',
        ids: new Set(['story1-ac1', 'story1-ac2']),
      },
      { file: 'b', level: 'backend-it', ids: new Set(['story1-ac1']) },
    ]
    const { rows, errors, warnings } = evaluate(features, refs)
    assert.deepEqual(rows[0].levels, ['backend-it', 'frontend'])
    assert.deepEqual(errors, [])
    assert.deepEqual(warnings, ['@story1-ac2 has tests; remove @wip'])
  })

  it('fails unknown and duplicate IDs', () => {
    const refs = [{ file: 'a', level: 'frontend', ids: new Set(['story9-ac1']) }]
    const { errors } = evaluate([...features, ...features], refs)
    assert.ok(errors.includes('a: references unknown scenario [story9-ac1]'))
    assert.ok(errors.some((e) => /duplicate scenario ID @story1-ac1/.test(e)))
  })

  it('keeps root and qualified IDs apart and resolves qualified references', () => {
    const root = parseFeature(FEATURE, 'stories/story1.feature')
    const sub = parseFeature(FEATURE, 'stories/assessment/story1.feature', 'assessment')
    const refs = [
      {
        file: 'a',
        level: 'backend-it',
        ids: new Set(['assessment/story1-ac1', 'story1-ac1']),
      },
      { file: 'b', level: 'frontend', ids: new Set(['assessment/story9-ac1']) },
    ]
    const { rows, errors } = evaluate([root, sub], refs)
    assert.deepEqual(
      rows.map((r) => [r.id, r.status]),
      [
        ['story1-ac1', 'covered'],
        ['story1-ac2', 'wip'],
        ['assessment/story1-ac1', 'covered'],
        ['assessment/story1-ac2', 'wip'],
      ],
    )
    assert.deepEqual(errors, ['b: references unknown scenario [assessment/story9-ac1]'])
  })

  it('detects duplicates among qualified IDs', () => {
    const sub = parseFeature(FEATURE, 'f', 'assessment')
    const { errors } = evaluate([sub, sub], [])
    assert.ok(errors.some((e) => /duplicate scenario ID @assessment\/story1-ac1/.test(e)))
  })
})
