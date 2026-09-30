// @vitest-environment node
// Design system rule 1 (docs/design-system.md): components use semantic colour tokens, never raw Tailwind palette
// classes or arbitrary hex values, so the theme stays in one place (src/index.css).
import ts from 'typescript';
import { describe, expect, it } from 'vitest';

const PALETTE =
  'slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo' +
  '|violet|purple|fuchsia|pink|rose';
const RAW_COLOUR = new RegExp(
  `\\b[a-z]+-(?:(?:${PALETTE})-\\d{2,3}|white|black|\\[#[0-9a-fA-F]{3,8}\\])(?![\\w-])`,
  'g',
);

const findRawColours = (text: string) => text.match(RAW_COLOUR) ?? [];

describe('design tokens', () => {
  it('source uses only semantic colour tokens', () => {
    const src = `${ts.sys.getCurrentDirectory()}/src/`;
    const files = ts.sys.readDirectory(src, ['.ts', '.tsx']).filter((file) => !/\.test\.tsx?$/.test(file));
    expect(files.length).toBeGreaterThan(0);
    const violations = files.flatMap((file) =>
      findRawColours(ts.sys.readFile(file) ?? '').map((cls) => `${file.slice(src.length)}: ${cls}`),
    );
    expect(violations).toEqual([]);
  });

  it('self-check: flags palette, white/black and hex classes but not tokens', () => {
    expect(findRawColours('bg-slate-100 text-blue-700 hover:bg-white border-[#fff] ring-black/10')).toEqual([
      'bg-slate-100',
      'text-blue-700',
      'bg-white',
      'border-[#fff]',
      'ring-black',
    ]);
    expect(findRawColours('bg-surface text-ink-muted bg-danger-soft ring-ink-inverse/20 bg-meter-3 w-1/5')).toEqual([]);
  });
});
