// Screenshots every screen at phone (390) and desktop (1280) width for a design review, into
// target/design/<tag>/. Needs design-seed.mjs to have run (it saves the logins and ids).
// Run from src/main/webui: node scripts/design-shots.mjs <tag> [screen,screen] [phone,desktop]
// FULL=0 shoots the viewport only (what a phone actually shows), instead of the full page.
import { mkdirSync, readFileSync } from 'node:fs';
import { chromium } from 'playwright';

const DIR = new URL('../../../../target/design', import.meta.url).pathname;
const [tag = 'shots', onlyArg, sizesArg] = process.argv.slice(2);
const only = onlyArg ? onlyArg.split(',') : null;
const sizes = sizesArg ? sizesArg.split(',') : null;
mkdirSync(`${DIR}/${tag}`, { recursive: true });
const { ids, trip, katechizm } = JSON.parse(readFileSync(`${DIR}/ids.json`, 'utf8'));
const base = 'http://localhost:8080';

const screens = [
  ['splash', null, '/'],
  ['public-anon', null, '/'],
  ['login', null, '/login'],
  ['public-parent', 'parent', '/'],
  ['public-treasurer', 'treasurer', '/'],
  ['dashboard', 'treasurer', '/dashboard'],
  ['collection-active', 'treasurer', `/collections/${trip}`],
  ['collection-settled', 'treasurer', `/collections/${katechizm}`],
  ['collection-parent', 'parent', `/collections/${trip}`],
  ['treasurer-panel', 'treasurer', '/treasurer'],
  ['student', 'parent', `/students/${ids['Kalina Kacza']}`],
  ['ledger', 'treasurer', '/ledger'],
  ['more', 'parent', '/wiecej'],
  ['how-it-works', null, '/jak-to-dziala'],
];
const widths = {
  phone: { width: 390, height: 844, deviceScaleFactor: 2 },
  desktop: { width: 1280, height: 800, deviceScaleFactor: 1 },
};

const browser = await chromium.launch();
for (const [size, vp] of Object.entries(widths)) {
  if (sizes && !sizes.includes(size)) continue;
  for (const [name, who, path] of screens) {
    if (only && !only.includes(name)) continue;
    const context = await browser.newContext({
      viewport: { width: vp.width, height: vp.height },
      deviceScaleFactor: vp.deviceScaleFactor,
      storageState: who ? `${DIR}/state-${who}.json` : undefined,
      reducedMotion: 'reduce',
    });
    const page = await context.newPage();
    if (name === 'splash') {
      // Hold the app's scripts back so the inline launch splash stays up for the shot.
      await page.route('**/*.js', () => {});
      await page.goto(`${base}${path}`, { waitUntil: 'commit' });
      await page.waitForTimeout(600);
    } else {
      await page.goto(`${base}${path}`);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(700);
    }
    await page.screenshot({ path: `${DIR}/${tag}/${size}-${name}.png`, fullPage: process.env.FULL !== '0' });
    await context.close();
  }
}
await browser.close();
console.log('shots done', tag);
