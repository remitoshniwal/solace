// Opens Blockbench (web version) in a headless browser, builds Solace's model with build_solace.js,
// saves it into the mod (src/main/resources/assets/solace/models/entity/solace.bbmodel) and takes preview pictures of both forms.
// Run:  node tools/models/bb_run.mjs [outDir]
// Needs Playwright with Chromium (PLAYWRIGHT path below) and internet access to web.blockbench.net.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const PLAYWRIGHT = process.env.PLAYWRIGHT || '/opt/node22/lib/node_modules/playwright/index.mjs';
const { chromium } = await import(PLAYWRIGHT);
const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, '../..');
const out = resolve(process.argv[2] || join(repo, 'build/model-previews'));
mkdirSync(out, { recursive: true });
const tex = name => 'data:image/png;base64,' + readFileSync(join(repo, 'src/main/resources/assets/solace/textures/entity', name)).toString('base64');

const browser = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-webgl', '--ignore-gpu-blocklist'] });
const page = await browser.newPage({ viewport: { width: 1900, height: 1000 } });
page.on('console', m => { if (m.type() === 'error') console.log('console error:', m.text().slice(0, 300)); });
page.on('pageerror', e => console.log('page error:', e.message.slice(0, 300)));
await page.goto('https://web.blockbench.net/', { waitUntil: 'load', timeout: 90000 });
await page.waitForFunction(() => typeof Blockbench !== 'undefined' && typeof Cube !== 'undefined', null, { timeout: 90000 });
await page.addScriptTag({ content: readFileSync(join(here, 'build_solace.js'), 'utf8') });

console.log(JSON.stringify(await page.evaluate(url => buildSolace(url), tex('solace_shade.png'))));
await page.waitForTimeout(800);

// Save the model (with the normal texture embedded, so it opens textured in Blockbench).
const model = await page.evaluate(() => Codecs.project.compile());
writeFileSync(join(repo, 'src/main/resources/assets/solace/models/entity/solace.bbmodel'), typeof model === 'string' ? model : JSON.stringify(model));

async function shoot(name, angleDeg, bg) {
  await page.evaluate(([a, bg]) => {
    const p = Preview.selected;
    const r = a * Math.PI / 180, d = 64;
    // Front is north (-z): angle 0 looks at its front.
    p.camera.position.set(Math.sin(r) * d, 26, -Math.cos(r) * d);
    p.controls.target.set(0, 18.5, 0);
    p.controls.update();
    if (typeof unselectAllElements === 'function') unselectAllElements();
    Canvas.scene.background = new THREE.Color(bg);
    for (const o of Canvas.scene.children) if (o.isGridHelper || o.name === 'grid_group' || o.name === 'grid') o.visible = false;
    if (Canvas.grid) Canvas.grid.visible = false;
    p.render();
  }, [angleDeg, bg]);
  await page.waitForTimeout(500);
  const rect = await page.evaluate(() => { const b = Preview.selected.canvas.getBoundingClientRect(); return { x: b.x, y: b.y, width: b.width, height: b.height }; });
  await page.screenshot({ path: join(out, name + '.png'), clip: rect });
}

for (const [neg, url, bg] of [[false, tex('solace_shade.png'), '#8a8d96'], [true, tex('solace_shade_negative.png'), '#1b1c22']]) {
  await page.evaluate(([n, u]) => showForm(n, u), [neg, url]);
  await page.waitForTimeout(800);
  const tag = neg ? 'negative' : 'normal';
  await shoot(tag + '_front', 20, bg);
  await shoot(tag + '_side', 100, bg);
  await shoot(tag + '_back', 200, bg);
}
await browser.close();
console.log('saved', out);
