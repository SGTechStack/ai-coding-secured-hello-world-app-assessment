const { chromium } = require('../../e2e/node_modules/playwright');
const { pathToFileURL } = require('node:url');
const path = require('node:path');
const fs = require('node:fs');
const assert = require('node:assert/strict');
(async () => {
  const browser = await chromium.launch({headless:true, channel:'msedge'});
  try {
    const page = await browser.newPage({viewport:{width:1440,height:1050}});
    await page.emulateMedia({reducedMotion:'reduce'});
    const errors=[];
    page.on('pageerror', e=>errors.push(e.message));
    const url=pathToFileURL(path.join(__dirname,'index.html')).href;
    await page.goto(url);
    await page.screenshot({path:path.join(__dirname,'desktop.png')});
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
    assert.equal(await page.locator('.panel').count(),10);
    await page.locator('#search').fill('NO_MATCH_8291');
    assert.equal(await page.locator('#no-results').isVisible(),true);
    await page.locator('#clear').click();
    await page.locator('#search').fill('proxy');
    const matches=await page.locator('.panel:visible').count();
    assert(matches>0 && matches<10);
    await page.locator('nav a[href="#cookies"]').click();
    assert.equal(await page.locator('.panel:visible').count(),10);
    await page.locator('#cookies details').first().locator('summary').click();
    assert.equal(await page.locator('#cookies details[open]').count(),1);
    await page.locator('#policy').selectOption('ROLE_ADMIN');
    assert.equal(await page.locator('#routes tbody tr:visible').count(),4);
    await page.locator('#policy').selectOption('');
    assert.equal(await page.locator('#routes tbody tr:visible').count(),13);
    const broken=await page.evaluate(()=>[...document.querySelectorAll('a[href^="#"]')].map(a=>a.getAttribute('href').slice(1)).filter(id=>!document.getElementById(id)));
    assert.deepEqual(broken,[]);
    for(const width of [390,760,1024]) {
      await page.setViewportSize({width,height:844});
      await page.goto(url);
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,`overflow at ${width}`);
      if(width===390){
        await page.screenshot({path:path.join(__dirname,'mobile.png')});
        await page.locator('#cookies').evaluate(el=>el.scrollIntoView({block:'start'}));
        await page.screenshot({path:path.join(__dirname,'mobile-panel.png')});
      }
    }
    await page.goto(url+'#reset');
    assert.equal(await page.locator('#reset').isVisible(),true);
    await page.locator('#reset details').first().locator('summary').focus();
    await page.keyboard.press('Enter');
    assert.equal(await page.locator('#reset details[open]').count(),1);
    assert.deepEqual(errors,[]);
    const html=fs.readFileSync(path.join(__dirname,'index.html'),'utf8');
    for(const [,href] of html.matchAll(/(?:href|src)="([^"#]+)"/g)) {
      if(!href.startsWith('http')) assert(fs.existsSync(path.resolve(__dirname,href)),href);
    }
    fs.writeFileSync(path.join(__dirname,'verification.json'),JSON.stringify({date:'2026-09-26',result:'passed',checks:['30 source anchors checked by build','local links and image paths','10 chapter targets','search / clear / no results','navigation resets search','source expansion with mouse and keyboard','13 routes / 4 ADMIN rows','no page errors','no document overflow at 390, 760, 1024 and 1440 pixels'],limitations:['Backend tests not executed','No live proxy or database checks','Screenshots require separate visual inspection']},null,2)+'\n');
    console.log('PASS: reader navigation, search, source controls, policy filter, local links, keyboard expansion and responsive widths.');
  } finally { await browser.close(); }
})().catch(e=>{console.error(e);process.exitCode=1;});
