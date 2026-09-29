// Renders every docs/diagrams/*.mmd to assets/diagrams/<name>-light.svg and <name>-dark.svg.
// The README shows the variant matching the reader's GitHub theme through <picture>.
//
//   node docs/diagrams/render.mjs     (needs network access the first time, to fetch the Mermaid CLI)
import { execFileSync } from 'node:child_process'
import { mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { basename, dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const MERMAID_CLI = '@mermaid-js/mermaid-cli@12.0.0'
const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const sourceDir = 'docs/diagrams'
const outputDir = 'assets/diagrams'
const themes = ['light', 'dark']

// Each diagram sits on its own opaque card, so it stays readable on any backdrop: viewers that
// ignore <picture> (such as IDE previews) show the light variant even on a dark page.
const CARDS = {
  light: { fill: '#ffffff', stroke: '#e4e0f0' },
  dark: { fill: '#100d1f', stroke: '#2a2447' },
}
const CARD_PADDING = 24
const CARD_RADIUS = 14

/** Grows the SVG's canvas by the card padding and paints the card behind the diagram. */
function addCard(svg, theme) {
  const root = /<svg\b[^>]*>/.exec(svg)
  const viewBox = root && /viewBox="([-\d.]+) ([-\d.]+) ([\d.]+) ([\d.]+)"/.exec(root[0])
  if (!viewBox) throw new Error('rendered SVG has no viewBox')
  const [x, y, width, height] = viewBox.slice(1).map(Number)
  const box = {
    x: x - CARD_PADDING,
    y: y - CARD_PADDING,
    width: width + CARD_PADDING * 2,
    height: height + CARD_PADDING * 2,
  }
  const { fill, stroke } = CARDS[theme]
  const openTag = root[0]
    .replace(viewBox[0], `viewBox="${box.x} ${box.y} ${box.width} ${box.height}"`)
    .replace(/\swidth="[\d.]+"/, ` width="${box.width}"`)
    .replace(/\sheight="[\d.]+"/, ` height="${box.height}"`)
  // Inline style, so none of Mermaid's embedded CSS can restyle the card.
  const card =
    `<rect x="${box.x + 0.5}" y="${box.y + 0.5}" width="${box.width - 1}" height="${box.height - 1}"` +
    ` rx="${CARD_RADIUS}" style="fill:${fill};stroke:${stroke};stroke-width:1"/>`
  return svg.replace(root[0], openTag + card)
}

mkdirSync(join(root, outputDir), { recursive: true })
const sources = readdirSync(join(root, sourceDir)).filter((file) => file.endsWith('.mmd'))

for (const source of sources) {
  const name = basename(source, '.mmd')
  for (const theme of themes) {
    // Paths stay relative to the repository root: npx is a .cmd shim on Windows, which only runs
    // through a shell, and relative paths keep spaces in the checkout path out of the command line.
    execFileSync(
      'npx',
      [
        '--yes',
        MERMAID_CLI,
        '--input', `${sourceDir}/${source}`,
        '--output', `${outputDir}/${name}-${theme}.svg`,
        '--configFile', `${sourceDir}/theme-${theme}.json`,
        '--backgroundColor', 'transparent',
      ],
      { cwd: root, stdio: 'inherit', shell: process.platform === 'win32' },
    )
    const output = join(root, outputDir, `${name}-${theme}.svg`)
    writeFileSync(output, addCard(readFileSync(output, 'utf8'), theme))
    console.log(`rendered ${outputDir}/${name}-${theme}.svg`)
  }
}
