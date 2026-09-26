import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(dir, '../..');
const data = JSON.parse(fs.readFileSync(path.join(dir, 'content.json'), 'utf8'));
const standardsMd = fs.readFileSync(path.join(dir, 'standards.md'), 'utf8');
const java = 'backend/src/main/java/com/example/helloauth/';
const config = java + 'config/';
// Match implementation, not comments. Each small excerpt is read from this snapshot.
const definitions = {
  chain: [config+'SecurityConfig.java', 'SecurityFilterChain securityFilterChain(', 17],
  access: [config+'SecurityConfig.java', '.authorizeHttpRequests(auth -> auth', 18],
  csrf: [config+'SecurityConfig.java', 'CsrfTokenRepository csrfTokenRepository()', 7],
  serializer: [config+'SecurityConfig.java', 'DefaultCookieSerializerCustomizer sessionCookieCustomizer(', 31],
  devchain: [config+'SecurityConfig.java', '@Profile("dev")', 11],
  headers: [config+'SecurityConfig.java', '.headers(headers -> headers', 7],
  provider: [config+'SecurityConfig.java', 'AuthenticationManager authenticationManager(', 9],
  strategy: [config+'SecurityConfig.java', 'SessionAuthenticationStrategy sessionAuthenticationStrategy(', 6],
  logout: [config+'SecurityConfig.java', '.logout(logout -> logout', 12],
  prod: ['backend/src/main/resources/application-prod.yml', 'spring:', 46],
  base: ['backend/src/main/resources/application.yml', 'spring:', 100],
  properties: [config+'AppProperties.java', '@ConfigurationProperties(prefix = "app")', 13],
  startup: [java+'HelloAuthApplication.java', '@SpringBootApplication', 13],
  login: [java+'auth/AuthController.java', '@PostMapping("/login")', 34],
  me: [java+'auth/AuthController.java', '@GetMapping("/me")', 10],
  client: ['frontend/src/lib/api.ts', 'export const API_BASE:', 28],
  authcontext: ['frontend/src/auth/auth-context.tsx', 'const signOut = useCallback(', 12],
  loginservice: [java+'auth/LoginService.java', 'public Authentication login(', 37],
  throttle: [java+'auth/IpThrottleService.java', 'private final Cache<String, SlidingWindowBucket> failureBuckets;', 20],
  adminvalidator: [config+'ProdAdminCredentialsValidator.java', 'public ProdAdminCredentialsValidator(', 24],
  tunables: [config+'SecurityTunablesValidator.java', 'public SecurityTunablesValidator(', 14],
  seeder: [java+'admin/AdminSeeder.java', 'public void seedAdminIfAbsent()', 34],
  email: [java+'passwordreset/EmailService.java', 'public void sendPasswordResetLink(', 11],
  resetservice: [java+'passwordreset/PasswordResetService.java', 'public void confirmReset(', 37],
  resetcontroller: [java+'passwordreset/PasswordResetController.java', '@RequestMapping("/api/auth/password-reset")', 18],
  admin: [java+'admin/AdminService.java', 'public AdminUserResponse setEnabled(', 16],
  errors: [java+'auth/ApiExceptionHandler.java', '@ExceptionHandler(LoginThrottledException.class)', 6],
  hello: [java+'HelloController.java', '@GetMapping("/api/hello")', 5],
  prodtest: ['backend/src/test/java/com/example/helloauth/ProdProfileTests.java', '@SpringBootTest(properties = {', 9],
  startuptest: ['backend/src/test/java/com/example/helloauth/StartupConfigValidationTests.java', 'void equalThrottleAndLockoutThresholdsRefuseToStart()', 13]
};
const evidence = Object.entries(definitions).map(([id, [file, needle, count]]) => {
  const lines = fs.readFileSync(path.join(root, file), 'utf8').split(/\r?\n/);
  const index = lines.findIndex(line => line.includes(needle));
  if(index < 0) throw new Error(`Missing source anchor ${id}: ${needle}`);
  const excerpt = lines.slice(index, index+count);
  return {id, path:file, symbol:needle, start:index+1, end:index+excerpt.length,
    revision:data.revision, kind:file.includes('/test/')?'test':file.endsWith('.yml')?'configuration':'implementation',
    excerpt:excerpt.join('\n'), supports:data.panels.filter(p=>p.sources.includes(id)).map(p=>p.id)};
});
const byId = Object.fromEntries(evidence.map(e=>[e.id,e]));
const esc = s => String(s).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
const table = (headers, rows) => `<div class="table-wrap" tabindex="0" aria-label="Scrollable ${esc(headers[0])} table"><table><thead><tr>${headers.map(s=>`<th scope="col">${esc(s)}</th>`).join('')}</tr></thead><tbody>${rows.map(r=>`<tr>${r.map(s=>`<td>${esc(s)}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`;
const mdTable = (headers, rows) => [headers, headers.map(()=> '---'), ...rows].map(row=>'| '+row.map(s=>s.replaceAll('|','\\|')).join(' | ')+' |').join('\n');
const sources = ids => ids.map(id=>{
  const e=byId[id];
  return `<details class="source"><summary>${esc(path.basename(e.path))} · L${e.start}–${e.end}</summary><p>${esc(e.kind)} · ${esc(e.path)} · ${data.revision.slice(0,12)}</p><pre><code>${esc(e.excerpt)}</code></pre><a href="../../${esc(e.path)}">Open full source file</a></details>`;
}).join('');
const stack = [
 ['Java', '21 declared target', 'Backend language; installed runtime not established'],
 ['Spring Boot', '4.1.1 declared parent', 'MVC, external configuration and auto-configuration'],
 ['Spring Security', 'BOM-managed; resolved version not verified', 'Filter chain, BCrypt, authentication and CSRF'],
 ['Spring Session JDBC', 'BOM-managed; resolved version not verified', 'Server-side session persistence; configured in application.yml'],
 ['Caffeine', 'BOM-managed; resolved version not verified', 'Process-local IP budgets in IpThrottleService'],
 ['Liquibase / Hibernate', 'BOM-managed; resolved versions not verified', 'Migration and schema-validation configuration'],
 ['React / Vite', `${JSON.parse(fs.readFileSync(path.join(root,'frontend/package-lock.json'),'utf8')).packages['node_modules/react'].version} / ${JSON.parse(fs.readFileSync(path.join(root,'frontend/package-lock.json'),'utf8')).packages['node_modules/vite'].version} lockfile`, 'AuthContext UI state / build-time API address']
];
const coverage = {
  scope:data.scope, date:data.date, revision:data.revision,
  initialDirtyState:'Review used the current working tree including existing uncommitted application and documentation changes; revision identifies base HEAD only.',
  inspectedFiles:[...new Set([...evidence.map(e=>e.path), java+'admin/AdminController.java', 'backend/pom.xml','frontend/package.json','frontend/package-lock.json','frontend/vite.config.ts','backend/src/main/resources/application-dev.yml'])],
  coverage:[
    {area:'Security chain, profiles, startup validators',status:'inspected'},
    {area:'Login, admin guards, reset, browser cookie transport',status:'partial',reason:'Representative paths inspected for this introduction; not a full audit.'},
    {area:'Tests',status:'partial',reason:'ProdProfileTests and StartupConfigValidationTests read, not executed.'},
    {area:'Infrastructure, live proxy, production database, frontend host headers',status:'not-inspected',reason:'Deployment not exercised; no runtime security claims.'},
    {area:'Remaining nine-topic playbook scope',status:'not-applicable',reason:'User requested an enhanced security/configuration introduction, not a complete playbook.'}
  ],
  exclusions:['Vendor dependencies, build output and historical assessment artifacts were not audited.'],
  routeCoverage:'Static inventory of application controller methods plus configured logout and health; H2 dev servlet is separate. Not a runtime route dump.',
  verification:'Source anchors checked during generation. Backend tests not run for this documentation-only change.'
};
fs.writeFileSync(path.join(dir,'evidence.json'),JSON.stringify(evidence,null,2)+'\n');
fs.writeFileSync(path.join(dir,'coverage.json'),JSON.stringify(coverage,null,2)+'\n');
fs.writeFileSync(path.join(dir,'stack.json'),JSON.stringify(stack,null,2)+'\n');
const glossary = [['Authentication','Proving who you are (logging in).'],['Authorization','Deciding what you are allowed to do.'],['CSRF','Cross-site request forgery: another site tricks your browser into sending a request. A validated session-bound token mitigates it; XSS can bypass it.'],['CORS','Browser rule for which other origins may read API responses. Not a login check or a firewall.'],['HttpOnly / Secure / SameSite','Cookie flags: JavaScript cannot read it / only sent over HTTPS / cross-site sending depends on the SameSite value (Strict in prod).'],['Rate limiting','Capping failed attempts per IP or per account to slow down password guessing.'],['Profile','A named set of Spring settings (dev, prod) switched on at startup.']];
// Archify diagrams: JSON source + delivered HTML in diagrams/, static PNG for Markdown in img/.
for (const id of Object.keys(data.diagrams)) {
  for (const file of [`diagrams/${id}.html`, `img/${id}.png`]) {
    if (!fs.existsSync(path.join(dir, file))) throw new Error(`Missing diagram asset ${file}`);
  }
}
for (const p of data.panels) for (const id of p.diagrams ?? []) {
  if (!data.diagrams[id]) throw new Error(`Panel ${p.id} references unknown diagram ${id}`);
}
const diagramMd = id => `![${data.diagrams[id]}](artifacts/introduction/img/${id}.png)\n\n*${data.diagrams[id]}.* [Open the interactive diagram](artifacts/introduction/diagrams/${id}.html)\n\n`;
// PNG IHDR holds width/height at bytes 16–23; used to reserve layout space for lazy images.
const pngSize = id => { const b = fs.readFileSync(path.join(dir, `img/${id}.png`)); return [b.readUInt32BE(16), b.readUInt32BE(20)]; };
const diagramHtml = id => { const [w,h] = pngSize(id); return `<figure class="diagram"><a href="diagrams/${id}.html" aria-label="Open interactive diagram: ${esc(data.diagrams[id])}"><img src="img/${id}.png" alt="${esc(data.diagrams[id])}" width="${w}" height="${h}" loading="lazy"></a><figcaption><span>${esc(data.diagrams[id])}</span><a href="diagrams/${id}.html">Open interactive diagram ↗</a></figcaption></figure>`; };
const readingMd=data.reading.map(([title,url,why])=>`- [${title}](${url}) — ${why}.`).join('\n');
let md=`# Hello Auth — ${data.title}\n\n${data.subtitle}\n\n[Open the illustrated comic reader](artifacts/introduction/index.html) · [Evidence index](artifacts/introduction/evidence.json) · [Coverage](artifacts/introduction/coverage.json)\n\n![Senior developer in mustard and junior developer in teal reviewing security together](artifacts/introduction/developers.png)\n\n**Snapshot:** ${data.date} · revision \`${data.revision}\`.\n\n**Scope:** ${data.scope}\n\n**How to read each panel:** the junior asks, the senior answers, a diagram shows the flow, and the bullets add detail. "In the code" means we read it in the source. "Deployment" and "Not known" mean it depends on how the app is run. Each panel ends with what to keep intact and links to the source.\n\n`;
for (const [i,p] of data.panels.entries()) {
 md+=`## ${String(i+1).padStart(2,'0')} · ${p.title}\n\n> **JUNIOR DEV:** ${p.question}\n\n**SENIOR DEV:** ${p.answer}\n\n`;
 for (const id of p.diagrams ?? []) md+=diagramMd(id);
 if(p.flow) md+=`**In short:** ${p.flow.join(' → ')}\n\n`;
 md+=p.notes.map(s=>`- ${s}`).join('\n')+`\n\n**What to preserve:** ${p.preserve}\n\n**Source anchors:** `+p.sources.map(id=>{const e=byId[id];return `[${path.basename(e.path)}:${e.start}](<${e.path}#L${e.start}>)`;}).join(' · ')+`\n\n`;
}
md+=standardsMd+"\n";
md+=`## Configuration reference\n\n${mdTable(['Setting','Shipped value / contract','Failure or limitation'],data.config)}\n\n## Endpoint access reference\n\nEvery application endpoint plus logout and health. Changes (POST, PATCH, DELETE) need a CSRF token even on public endpoints. The dev-only H2 console is not listed. Passing the filter rules does not mean the handler will accept the request.\n\n${mdTable(['Method','Path','Boundary policy','Additional condition'],data.routes)}\n\n## Technology orientation\n\nVersions come from backend/pom.xml and frontend/package-lock.json. Versions managed by the Spring Boot BOM were not looked up.\n\n${mdTable(['Technology','Version evidence','Role'],stack)}\n\n## Short glossary\n\n${mdTable(['Term','Meaning'],glossary)}\n\n## Further reading\n\nOfficial documentation for background. Links checked ${data.date}.\n\n${readingMd}\n\n## Coverage and review limits\n\n${coverage.coverage.map(c=>`- **${c.area}: ${c.status}.** ${c.reason??'Current working-tree source reviewed; revision is base HEAD.'}`).join('\n')}\n\nRepresentative source excerpts are in [evidence.json](artifacts/introduction/evidence.json). Nothing here was tested against a running system: no backend tests, live proxy or production database.\n\nTo regenerate this file and the comic reader, run \`node artifacts/introduction/build.mjs\`. Standards mappings are maintained in \`artifacts/introduction/standards.md\`; chapter text is in \`artifacts/introduction/content.json\`. Diagram sources are in \`artifacts/introduction/diagrams/*.json\` (Archify). Open \`artifacts/introduction/index.html\` in a browser; no server is needed.\n`;
fs.writeFileSync(path.join(root,'introduction.md'),md);
const html=`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="referrer" content="no-referrer"><title>Hello Auth — Security, panel by panel</title><link rel="stylesheet" href="reader.css"></head><body>
<a class="skip" href="#chapters">Skip to the comic</a>
<header class="masthead"><a href="#top" class="brand">HELLO AUTH<span> / FIELD NOTES</span></a><span>ISSUE 01 · SECURITY & CONFIGURATION</span><button id="print" type="button">Print / Save PDF</button></header>
<div class="shell"><aside><p class="eyebrow">Inside this issue</p><nav aria-label="Chapters">${data.panels.map((p,i)=>`<a href="#${p.id}"><span>${String(i+1).padStart(2,'0')}</span>${esc(p.title)}</a>`).join('')}<a href="#reference"><span>11</span>Configuration & access</a><a href="#coverage"><span>12</span>Sources & coverage</a></nav><div class="side-note">Read the policy.<br>Follow the mechanism.<br><strong>Check the boundary.</strong></div></aside>
<main id="top"><section class="hero"><div class="hero-copy"><p class="eyebrow">A conversation in ten panels</p><h1>Two files.<br>A bigger<br><em>security story.</em></h1><p>${esc(data.subtitle)}</p><a class="start" href="#boundary">Start the conversation <span>↗</span></a></div><figure><img src="developers.png" alt="Senior developer in mustard explaining a security review to a junior developer in teal" width="1536" height="1024"><figcaption><span>THE SENIOR · connect the mechanisms</span><span>THE JUNIOR · question the assumptions</span></figcaption></figure></section>
<div class="snapshot"><span>INSPECTED ${data.date}</span><span>REV ${data.revision.slice(0,12)}</span><span>SOURCE REVIEW · NO LIVE AUDIT</span></div>
<div class="scope"><strong>The boundary of this story</strong><p>${esc(data.scope)}</p></div>
<form class="search" role="search" onsubmit="return false"><label for="search">Find a concept</label><input id="search" type="search" placeholder="Try cookies, proxy, APP_ADMIN or 401…" aria-describedby="search-status"><button id="clear" type="button">Clear</button></form><p id="search-status" class="search-status" role="status" aria-live="polite">10 panels · source excerpts expand below each panel</p><p id="no-results" hidden>No matching panel. Try “CSRF”, “profile” or “proxy”, or clear the search.</p>
<div id="chapters">${data.panels.map((p,i)=>`<article class="panel" id="${p.id}"><div class="panel-heading"><span class="number">${String(i+1).padStart(2,'0')}</span><div><p class="eyebrow">${esc(p.category)}</p><h2>${esc(p.title)}</h2></div><a class="permalink" href="#${p.id}" aria-label="Link to ${esc(p.title)}">#</a></div><div class="dialogue junior"><div class="portrait junior-art" role="img" aria-label="Junior developer"></div><div class="bubble"><p class="speaker">JUNIOR DEV <span>asks</span></p><p>${esc(p.question)}</p></div></div><div class="dialogue senior"><div class="portrait senior-art" role="img" aria-label="Senior developer"></div><div class="bubble"><p class="speaker">SENIOR DEV <span>explains</span></p><p>${esc(p.answer)}</p></div></div>${(p.diagrams??[]).map(diagramHtml).join('')}${p.flow?`<div class="flow" aria-label="Conceptual flow">${p.flow.map((s,i)=>`<div><span>${i+1}</span>${esc(s)}</div>`).join('')}</div><p class="caption">Simplified view, not a runtime trace.</p>`:''}<div class="notes">${p.notes.map(s=>{const n=s.indexOf(':');return `<p><strong>${esc(s.slice(0,n+1))}</strong>${esc(s.slice(n+1))}</p>`;}).join('')}</div><div class="preserve"><strong>Keep this intact</strong><p>${esc(p.preserve)}</p></div><div class="evidence"><p class="eyebrow">Inspect the evidence · observed source</p>${sources(p.sources)}</div></article>`).join('')}</div>
<section class="reference" id="reference"><p class="eyebrow">11 / Desk reference</p><h2>Settings and access, without the guesswork.</h2><h3>Configuration contract</h3>${table(['Setting','Shipped value / contract','Failure or limitation'],data.config)}<h3>Application endpoint register</h3><p>Static controller inventory plus logout and health. Public mutations still require CSRF. H2’s dev servlet is separate. Handler and service rules can reject a chain-permitted request.</p><label for="policy">Filter by boundary policy</label><select id="policy"><option value="">All policies</option><option value="Public">Public</option><option value="ROLE_ADMIN">ADMIN</option><option value="Authenticated">Authenticated</option><option value="CSRF">CSRF protected</option></select><div id="routes">${table(['Method','Path','Boundary policy','Additional condition'],data.routes)}</div><h3>Technology orientation</h3><p>Backend version declarations: backend/pom.xml. Frontend resolutions: frontend/package-lock.json. This is a scoped stack register; managed backend versions were not resolved.</p>${table(['Technology','Version evidence','Role'],stack)}<h3>Small glossary</h3>${table(['Term','Meaning'],glossary)}</section>
<section class="reference" id="coverage"><p class="eyebrow">12 / Evidence & limits</p><h2>Know what this review establishes.</h2><p>Observed = source inspected. Interpretation = explanatory inference. Recommendation = proposed improvement. Unknown = needs further evidence.</p>${table(['Area','Coverage','Limit'],coverage.coverage.map(c=>[c.area,c.status,c.reason??'Current working-tree source inspected; revision is base HEAD.']))}<p>Initial snapshot: ${esc(coverage.initialDirtyState)} ${esc(coverage.verification)}</p><p><a href="evidence.json">Source evidence & excerpts</a> · <a href="coverage.json">Coverage ledger</a> · <a href="content.json">Structured chapter content</a> · <a href="../../introduction.md">Markdown edition</a></p><h3>Standards assessment</h3><p><a href="../../introduction.md#standards-and-guidance-implemented">Read the standards mappings and limitations in the Markdown edition</a> · <a href="standards.md">Standards source</a></p><h3>Read the framework background</h3><p>Official references checked ${data.date}; external documentation is not proof of repository behavior.</p><ul>${data.reading.map(([title,url,why])=>`<li><a href="${url}" rel="noreferrer">${esc(title)}</a> — ${esc(why)}<small>${new URL(url).hostname}</small></li>`).join('')}</ul></section>
<footer>HELLO AUTH / FIELD NOTES <span>Evidence before confidence. <a href="#top">Back to top ↑</a></span></footer></main></div><script src="reader.js"></script></body></html>`;
fs.writeFileSync(path.join(dir,'index.html'),html);
console.log(`Built introduction.md and comic reader; verified ${evidence.length} source anchors across ${coverage.inspectedFiles.length} files.`);
