// Renders the app icon (ink-blue squared paper, white piggy bank, gold coin) to every PNG
// size the manifest and iOS need, plus favicon.svg and favicon.ico. See docs/DESIGN.md,
// "App icon, favicon, splash". Run from src/main/webui: node scripts/render-icons.mjs
import { writeFileSync } from 'node:fs';
import { chromium } from 'playwright';

const OUT = new URL('../public', import.meta.url).pathname;

// Material Symbols "savings" (the same mark as shared/logo).
const PIG = 'M230-120q-21 0-39.49-13.96Q172.02-147.93 166-168q-25-86-41.54-148.46-16.54-62.46-26.37-109.68-9.82-47.22-13.95-83.79Q80-546.49 80-580q0-92 64-156t156-64h200q27-36 68.5-58t91.5-22q25 0 42.5 17.5T720-820q0 6-1.5 12t-3.5 11q-4 11-7.5 22t-5.5 24l91 91h57q12.75 0 21.38 8.62Q880-642.75 880-630v227q0 10.24-5.5 18.12Q869-377 859-374l-91.93 30.3L713-163q-6.03 19.61-21.84 31.31Q675.34-120 655-120H540q-24.75 0-42.37-17.63Q480-155.25 480-180v-20h-80v20q0 24.75-17.62 42.37Q364.75-120 340-120H230Zm410-400q17 0 28.5-11.5T680-560q0-17-11.5-28.5T640-600q-17 0-28.5 11.5T600-560q0 17 11.5 28.5T640-520ZM490-620q12.75 0 21.38-8.68 8.62-8.67 8.62-21.5 0-12.82-8.62-21.32-8.63-8.5-21.38-8.5H350q-12.75 0-21.37 8.68-8.63 8.67-8.63 21.5 0 12.82 8.63 21.32 8.62 8.5 21.37 8.5h140Z';

// Full-bleed square (maskable: the OS crops it; the pig stays inside the 80% safe zone).
// The squared-paper grid is dropped at small sizes, where it only reads as noise.
function appIcon({ grid }) {
  let lines = '';
  if (grid) {
    for (let p = 32; p < 512; p += 32) {
      lines += `<path d="M${p} 0V512M0 ${p}H512" stroke="#ffffff" stroke-opacity="0.07" stroke-width="2"/>`;
    }
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
  <defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#2b4fd6"/><stop offset="1" stop-color="#1d38a3"/></linearGradient></defs>
  <rect width="512" height="512" fill="url(#g)"/>${lines}
  <g transform="translate(76 452) scale(0.375)">
    <path d="${PIG}" fill="#ffffff"/>
    <circle cx="300" cy="-800" r="62" fill="#f4b400" stroke="#c98f00" stroke-width="14"/>
    <path d="M300 -836v72M264 -800h72" stroke="#c98f00" stroke-width="13" stroke-linecap="round"/>
  </g>
</svg>`;
}

// Favicon: no background square, just the ink-blue pig - reads on light and dark tabs.
const favicon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960" fill="none">
  <path d="${PIG}" fill="#2445c4"/>
  <circle cx="300" cy="-790" r="58" fill="#f4b400" stroke="#c98f00" stroke-width="12"/>
  <path d="M300 -822v64M268 -790h64" stroke="#c98f00" stroke-width="11" stroke-linecap="round"/>
</svg>
`;

// An .ico holding PNG-encoded images (supported by every browser that still asks for one).
function ico(pngs) {
  const header = Buffer.alloc(6 + 16 * pngs.length);
  header.writeUInt16LE(0, 0);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(pngs.length, 4);
  let offset = header.length;
  pngs.forEach(({ size, data }, i) => {
    const at = 6 + 16 * i;
    header.writeUInt8(size, at);
    header.writeUInt8(size, at + 1);
    header.writeUInt16LE(1, at + 4);
    header.writeUInt16LE(32, at + 6);
    header.writeUInt32LE(data.length, at + 8);
    header.writeUInt32LE(offset, at + 12);
    offset += data.length;
  });
  return Buffer.concat([header, ...pngs.map((p) => p.data)]);
}

const browser = await chromium.launch();
const page = await browser.newPage();

async function render(svg, size, path) {
  await page.setViewportSize({ width: size, height: size });
  const sized = svg.replace('<svg ', `<svg width="${size}" height="${size}" `);
  await page.setContent(`<html><body style="margin:0">${sized}</body></html>`);
  return page.screenshot({ path, omitBackground: true, clip: { x: 0, y: 0, width: size, height: size } });
}

for (const size of [72, 96, 128, 144, 152, 192, 384, 512]) {
  await render(appIcon({ grid: size >= 128 }), size, `${OUT}/icons/icon-${size}x${size}.png`);
}
await render(appIcon({ grid: true }), 180, `${OUT}/icons/apple-touch-icon.png`);

writeFileSync(`${OUT}/favicon.svg`, favicon);
const favPngs = [];
for (const size of [16, 32, 48]) {
  favPngs.push({ size, data: await render(favicon, size) });
}
writeFileSync(`${OUT}/favicon.ico`, ico(favPngs));

await browser.close();
console.log('icons rendered');
