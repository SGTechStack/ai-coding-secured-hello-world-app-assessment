// Temporary helper: screenshot the diagram SVG of an Archify HTML file.
// Usage: node shot.cjs <input.html> <output.png> [light|dark]
const { chromium } = require('../../../e2e/node_modules/playwright');
const { pathToFileURL } = require('node:url');
const path = require('node:path');
(async () => {
  const [input, output, scheme = 'light'] = process.argv.slice(2);
  const browser = await chromium.launch({ headless: true, channel: 'msedge' });
  try {
    const page = await browser.newPage({ viewport: { width: 1600, height: 1000 }, deviceScaleFactor: 2, colorScheme: scheme });
    await page.goto(pathToFileURL(path.resolve(input)).href);
    // Static image for Markdown: hide interactive viewer chrome (print-only content remains).
    await page.addStyleTag({ content: '.no-print, .header button, .header-row button, .export-wrap, .preset-wrap { display: none !important; } button { visibility: hidden !important; } .container { padding: 24px !important; }' });
    await page.waitForTimeout(400);
    const target = page.locator('.container').first();
    await ((await target.count()) ? target : page.locator('body')).screenshot({ path: output });
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
