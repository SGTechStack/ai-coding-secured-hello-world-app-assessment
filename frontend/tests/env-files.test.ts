import { readFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

const envFile = (name: string) =>
  Object.fromEntries(
    readFileSync(path.resolve(import.meta.dirname, '..', name), 'utf8')
      .split(/\r?\n/)
      .filter((line) => line.trim() && !line.trimStart().startsWith('#'))
      .map((line) => [line.slice(0, line.indexOf('=')).trim(), line.slice(line.indexOf('=') + 1).trim()]),
  )

describe('.env.production', () => {
  it('T-CFG-034: the key set is exactly VITE_CSP and VITE_API_ORIGIN', () => {
    expect(Object.keys(envFile('.env.production')).sort()).toEqual(['VITE_API_ORIGIN', 'VITE_CSP'])
  })

  it('T-CFG-035: the committed VITE_API_ORIGIN is still the placeholder', () => {
    expect(envFile('.env.production').VITE_API_ORIGIN).toBe('https://api.example.invalid')
  })
})
