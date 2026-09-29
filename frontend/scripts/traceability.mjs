#!/usr/bin/env node
// Scenario → test traceability check.
//
// Every scenario in stories/**/*.feature carries one ID tag, `@story<N>-ac<M>`, where M is the
// scenario's position in its feature. Features in a subdirectory get IDs qualified by that
// directory, e.g. `assessment/story1-ac1`. A test claims a scenario by putting `[story<N>-ac<M>]`
// (or `[<dir>/story<N>-ac<M>]`) in its name.
//
// Fails when a scenario without @wip has no test, or a test references an unknown ID.
// Scans the whole repository (stories/, frontend and backend tests), not just the frontend.
// Usage: npm run trace (in frontend/), or node frontend/scripts/traceability.mjs

import { readFileSync, readdirSync } from 'node:fs'
import { basename, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const ID_TAG = /^@(story\d+-ac(\d+))$/
// Optionally qualified by a feature's directory below stories/, e.g. [assessment/story1-ac1].
const TEST_REF = /\[((?:[\w.-]+\/)*story\d+-ac\d+)\]/g
const SKIP_DIRS = new Set(['node_modules', 'target', 'dist', 'coverage', '.git'])

export const LEVELS = {
  frontend: { dir: 'frontend/src', match: /\.test\.tsx?$/ },
  backend: { dir: 'backend/src/test/java', match: /\.java$/ },
}

/**
 * Parses one .feature file into { title, scenarios: [{ id, name, wip }] } plus errors. A feature in
 * a subdirectory of stories/ passes that directory as `qualifier`, so its IDs become
 * `<qualifier>/story<N>-ac<M>` while its tags stay unqualified.
 */
export function parseFeature(text, file, qualifier = '') {
  const errors = []
  const scenarios = []
  const prefixes = new Set()
  let title
  let tags = []
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim()
    if (line.startsWith('Feature:')) {
      title = line.slice('Feature:'.length).trim()
      tags = []
    } else if (line.startsWith('@')) tags.push(...line.split(/\s+/))
    else if (/^Scenario( Outline)?:/.test(line)) {
      const name = line.slice(line.indexOf(':') + 1).trim()
      const ids = tags.map((t) => ID_TAG.exec(t)).filter(Boolean)
      const position = scenarios.length + 1
      if (ids.length !== 1) {
        errors.push(`${file}: scenario "${name}" needs exactly one @story<N>-ac<M> tag`)
      } else if (Number(ids[0][2]) !== position) {
        errors.push(`${file}: scenario "${name}" is #${position} but tagged @${ids[0][1]}`)
      }
      const tag = ids[0]?.[1]
      if (tag) prefixes.add(tag.split('-')[0])
      const id = tag && qualifier ? `${qualifier}/${tag}` : tag
      scenarios.push({ id, name, wip: tags.includes('@wip') })
      tags = []
    } else if (line && !line.startsWith('#')) tags = []
  }
  if (prefixes.size > 1) errors.push(`${file}: mixes story prefixes ${[...prefixes].join(', ')}`)
  return { title, scenarios, errors }
}

/** Scenario IDs a test file claims through bracketed IDs in its test names. */
export function findReferences(text) {
  return new Set([...text.matchAll(TEST_REF)].map((m) => m[1]))
}

/** Levels are keyed by name; backend splits into unit (*Test) and integration (*IT). */
export function levelOf(level, file) {
  if (level !== 'backend') return level
  return basename(file, '.java').endsWith('IT') ? 'backend-it' : 'backend-unit'
}

/** Combines parsed features and test references into a per-scenario matrix. */
export function evaluate(features, references) {
  const errors = features.flatMap((f) => f.errors)
  const warnings = []
  const coverage = new Map()
  const seen = new Set()
  for (const feature of features) {
    for (const s of feature.scenarios) {
      if (!s.id) continue
      if (seen.has(s.id)) errors.push(`duplicate scenario ID @${s.id}`)
      seen.add(s.id)
      coverage.set(s.id, new Set())
    }
  }
  for (const { file, level, ids } of references) {
    for (const id of ids) {
      if (coverage.has(id)) coverage.get(id).add(level)
      else errors.push(`${file}: references unknown scenario [${id}]`)
    }
  }
  const rows = features.flatMap((f) =>
    f.scenarios
      .filter((s) => s.id)
      .map((s) => {
        const levels = [...coverage.get(s.id)].sort()
        if (!levels.length && !s.wip) errors.push(`@${s.id} "${s.name}" has no test`)
        if (levels.length && s.wip) warnings.push(`@${s.id} has tests; remove @wip`)
        const status = levels.length ? 'covered' : s.wip ? 'wip' : 'MISSING'
        return { id: s.id, status, levels, name: s.name }
      }),
  )
  return { rows, errors, warnings }
}

function* walk(dir) {
  let entries
  try {
    entries = readdirSync(dir, { withFileTypes: true })
  } catch {
    return
  }
  for (const entry of entries) {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) {
      if (!SKIP_DIRS.has(entry.name)) yield* walk(path)
    } else yield path
  }
}

/** Feature files below `storiesDir`, as sorted '/'-separated relative paths. */
export function findFeatureFiles(storiesDir) {
  return [...walk(storiesDir)]
    .filter((path) => path.endsWith('.feature'))
    .map((path) => relative(storiesDir, path).replaceAll('\\', '/'))
    .sort()
}

/** The ID qualifier of a feature path relative to stories/: its directory, or '' at the root. */
export function qualifierOf(relativePath) {
  const parts = relativePath.replaceAll('\\', '/').split('/')
  return parts.slice(0, -1).join('/')
}

function run(root) {
  const storiesDir = join(root, 'stories')
  const features = findFeatureFiles(storiesDir).map((f) =>
    parseFeature(readFileSync(join(storiesDir, f), 'utf8'), `stories/${f}`, qualifierOf(f)),
  )

  const references = []
  for (const [level, { dir, match }] of Object.entries(LEVELS)) {
    for (const path of walk(join(root, dir))) {
      if (!match.test(path)) continue
      const file = relative(root, path).replaceAll('\\', '/')
      const ids = findReferences(readFileSync(path, 'utf8'))
      references.push({ file, level: levelOf(level, path), ids })
    }
  }

  const { rows, errors, warnings } = evaluate(features, references)
  rows.sort((a, b) => a.id.localeCompare(b.id, 'en', { numeric: true }))
  const width = Math.max(...rows.map((r) => r.id.length))
  for (const r of rows) {
    const levels = r.levels.join(', ') || '-'
    console.log(`${r.id.padEnd(width)}  ${r.status.padEnd(7)}  ${levels.padEnd(32)}  ${r.name}`)
  }
  for (const w of warnings) console.warn(`warning: ${w}`)
  for (const e of errors) console.error(`error: ${e}`)
  const covered = rows.filter((r) => r.status === 'covered').length
  const wip = rows.filter((r) => r.status === 'wip').length
  console.log(
    `\n${covered}/${rows.length} scenarios covered, ${wip} @wip, ${errors.length} error(s)`,
  )
  return errors.length ? 1 : 0
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  // This file lives in frontend/scripts/, so the repository root is two directories up.
  process.exitCode = run(fileURLToPath(new URL('../../', import.meta.url)))
}
