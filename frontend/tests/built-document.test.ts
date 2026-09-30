import { mkdtempSync, readdirSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { build, loadEnv, resolveConfig } from 'vite'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'

const root = path.resolve(import.meta.dirname, '..')
const outDir = mkdtempSync(path.join(tmpdir(), 'shw-build-'))
let html = ''

/** Every <meta>, <script> and <link> start tag in the built document, in document order. */
const tags = () =>
  [...html.matchAll(/<(meta|script|link)\b[^>]*>/gi)].map((m) => ({ name: m[1].toLowerCase(), tag: m[0] }))
const cspMetaIndex = () => tags().findIndex((t) => /http-equiv="Content-Security-Policy"/i.test(t.tag))
/** The meta-delivered policy, split into trimmed directives. */
const metaDirectives = () =>
  /content="([^"]*)"/
    .exec(tags()[cspMetaIndex()].tag)![1]
    .split(';')
    .map((d) => d.trim())

describe('built index.html (vite build, mode production)', () => {
  beforeAll(async () => {
    await build({ root, mode: 'production', logLevel: 'silent', build: { outDir, emptyOutDir: true } })
    html = readFileSync(path.join(outDir, 'index.html'), 'utf8')
  }, 120_000)
  afterAll(() => rmSync(outDir, { recursive: true, force: true }))

  it('T-BLD-010: the CSP meta tag precedes every script and link element', () => {
    const csp = cspMetaIndex()
    expect(csp).toBeGreaterThanOrEqual(0)
    const firstScriptOrLink = tags().findIndex((t) => t.name !== 'meta')
    expect(firstScriptOrLink).toBeGreaterThan(csp)
  })

  it('REJ-053: the Referrer-Policy meta tag is the first element in <head>', () => {
    const head = html.slice(html.search(/<head>/i) + '<head>'.length)
    expect(head.trimStart()).toMatch(/^<meta name="referrer" content="no-referrer"\s*\/?>/)
  })

  it('T-BLD-005: the emitted static bundle holds no .git or .svn', () => {
    const entries = readdirSync(outDir, { recursive: true, encoding: 'utf8' })
    expect(entries).toContain('index.html')
    expect(entries.filter((entry) => entry.split(/[\\/]/).some((part) => part === '.git' || part === '.svn'))).toEqual(
      [],
    )
  })

  it('T-BLD-002: the built index.html has no inline script', () => {
    const scripts = [...html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)]
    expect(scripts.length).toBeGreaterThan(0)
    for (const [, attrs, body] of scripts) {
      expect(attrs).toMatch(/\bsrc="/)
      expect(attrs).not.toMatch(/type="importmap"/)
      expect(body.trim()).toBe('')
    }
  })

  it('T-BLD-009: no %VITE_ placeholder and no localhost in connect-src', () => {
    expect(html).not.toContain('%VITE_')
    const connectSrc = metaDirectives().find((d) => d.startsWith('connect-src '))
    expect(connectSrc).toBeDefined()
    expect(connectSrc).not.toMatch(/localhost|127\.0\.0\.1/)
  })

  it('REJ-054, ADR-060: the meta policy keeps one unsplit style-src and no frame-ancestors', () => {
    const directives = metaDirectives()
    expect(directives.filter((d) => d.startsWith('style-src'))).toEqual(["style-src 'self'"])
    expect(directives.some((d) => d.startsWith('frame-ancestors'))).toBe(false)
  })
})

describe('effective Vite config', () => {
  it('T-BLD-003: build.chunkImportMap is false and build.assetsInlineLimit is 0', async () => {
    const config = await resolveConfig({ root, logLevel: 'silent' }, 'build', 'production')
    expect(config.build.chunkImportMap ?? false).toBe(false)
    expect(config.build.assetsInlineLimit).toBe(0)
  })

  it("R-HDR-010: the dev and preview servers send the mode's document CSP plus frame-ancestors 'none'", async () => {
    const header = async (mode: string, isPreview: boolean) => {
      const config = await resolveConfig({ root, logLevel: 'silent' }, 'serve', mode, mode, isPreview)
      return (isPreview ? config.preview : config.server).headers?.['Content-Security-Policy']
    }
    const expected = (mode: string) => `${loadEnv(mode, root, 'VITE_').VITE_CSP}; frame-ancestors 'none'`
    expect(await header('development', false)).toBe(expected('development'))
    expect(await header('production', true)).toBe(expected('production'))
  })
})
