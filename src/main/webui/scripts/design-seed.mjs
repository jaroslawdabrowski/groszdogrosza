// Seeds realistic demo data into the LOCAL dev DB for design review screenshots (see
// design-shots.mjs and docs/DESIGN.md). Needs `./mvnw quarkus:dev` running and an empty
// table - it is not idempotent. The treasurer (Jarek, child Leon) goes straight into
// DynamoDB (the bootstrap gap, same as e2e/seed.ts); everything else goes through the real
// API as that treasurer. Run from src/main/webui: node scripts/design-seed.mjs
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { chromium } from 'playwright';

const OUT = new URL('../../../../target/design', import.meta.url).pathname;
// GG_BASE_URL when the dev server runs on another port (e.g. 8081 next to another project).
const base = process.env.GG_BASE_URL ?? 'http://localhost:8080';
mkdirSync(OUT, { recursive: true });

function localstack() {
  if (process.env.GG_LOCALSTACK_URL) {
    return process.env.GG_LOCALSTACK_URL;
  }
  const line = execFileSync('docker', ['ps', '--format', '{{.Image}}\t{{.Ports}}']).toString()
    .split('\n').find((l) => l.startsWith('localstack/localstack'));
  return `http://localhost:${line.match(/0\.0\.0\.0:(\d+)->4566\/tcp/)[1]}`;
}

function putItem(item) {
  execFileSync('aws', ['dynamodb', 'put-item', '--table-name', 'groszdogrosza', '--item', JSON.stringify(item),
    '--endpoint-url', localstack(), '--region', 'eu-central-1'],
    { env: { ...process.env, AWS_ACCESS_KEY_ID: 'test', AWS_SECRET_ACCESS_KEY: 'test' } });
}

const leonId = randomUUID();
const treasurerId = randomUUID();
putItem({ pk: { S: `STUDENT#${leonId}` }, sk: { S: 'STUDENT' }, id: { S: leonId }, firstName: { S: 'Leon' },
  lastName: { S: 'Dąbrowski' }, piggyBankBalance: { N: '0' } });
putItem({ pk: { S: `PARENT#${treasurerId}` }, sk: { S: 'PARENT' }, id: { S: treasurerId }, studentId: { S: leonId },
  firstName: { S: 'Jarek' }, lastName: { S: 'Dąbrowski' }, email: { S: 'skarbnik@example.com' },
  expectedSenderName: { S: 'Jarosław Dąbrowski' }, cognitoSubjectId: { NULL: true }, role: { S: 'TREASURER' },
  bankAccountNumber: { NULL: true }, blikPhoneNumber: { NULL: true } });

// Logs in through the real Keycloak page; the saved storage state lets design-shots.mjs
// open pages as that user.
async function login(browser, user) {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(`${base}/login`);
  await page.locator('main').getByRole('button', { name: /Zaloguj/ }).first().click();
  await page.waitForURL(/\/realms\//);
  await page.locator('#username').fill(user);
  await page.locator('#password').fill(user);
  await page.locator('#kc-login').click();
  await page.waitForURL((u) => !u.pathname.includes('/realms/'));
  await page.waitForLoadState('networkidle');
  return { context, page };
}

const browser = await chromium.launch();
const { context, page } = await login(browser, 'skarbnik');
const token = await page.evaluate(() => localStorage.getItem('id_token'));

async function api(method, path, data) {
  const resp = await context.request.fetch(`${base}${path}`, { method, data, headers: { Authorization: `Bearer ${token}` } });
  if (!resp.ok()) {
    throw new Error(`${method} ${path}: ${resp.status()} ${await resp.text()}`);
  }
  const text = await resp.text();
  return text ? JSON.parse(text) : null;
}

const names = [['Miłosz', 'Deręgowski'], ['Eryk', 'Fuglewicz'], ['Michał', 'Jarosz'], ['Błażej', 'Jezuit'],
  ['Kalina', 'Kacza'], ['Miłosz', 'Klimasz'], ['Maksym', 'Kościółek'], ['Alicja', 'Krawczyk'], ['Ernest', 'Paluch'],
  ['Amelia', 'Rachwalska'], ['Zuzanna', 'Rosół'], ['Ignacy', 'Sitek'], ['Jakub', 'Stolarz'], ['Kornelia', 'Wojton'],
  ['Magdalena', 'Woś'], ['Artur', 'Zaskalski']];
const ids = { 'Leon Dąbrowski': leonId };
for (const [first, last] of names) {
  ids[`${first} ${last}`] = (await api('POST', '/api/students', { firstName: first, lastName: last })).id;
}
// rodzic1 / rodzic2 in keycloak-realm.json.
await api('POST', `/api/students/${ids['Kalina Kacza']}/parents`, { firstName: 'Anna', lastName: 'Kacza',
  email: 'anna.testowa@example.com', expectedSenderName: 'Anna Kacza' });
await api('POST', `/api/students/${ids['Zuzanna Rosół']}/parents`, { firstName: 'Piotr', lastName: 'Rosół',
  email: 'piotr.testowy@example.com', expectedSenderName: 'Piotr Rosół' });
await api('PUT', `/api/parents/${treasurerId}/payment-info`, { bankAccountNumber: '02 1140 2004 0000 3002 7827 2084',
  blikPhoneNumber: '692 544 922' });

const all = Object.values(ids);
// 1. Settled: everyone paid 18 zł, it cost 16 zł each - 2 zł each went back to the piggy banks.
const katechizm = (await api('POST', '/api/collections', { title: 'Katechizm do Religii',
  description: 'Podręczniki do religii na cały rok', baseAmountPerStudent: 18, studentIds: all })).id;
for (const id of all) {
  await api('POST', `/api/collections/${katechizm}/contributions`, { studentId: id, amount: 18 });
}
await api('POST', `/api/collections/${katechizm}/settle`, { actualCostSpent: 16 * all.length });
// 2. Active and small: those 2 zł leftovers cover half of it for everyone.
const chlopak = (await api('POST', '/api/collections', { title: 'Dzień chłopaka 2026',
  description: 'Prezenty dla chłopców z klasy - 30 września', baseAmountPerStudent: 4, studentIds: all })).id;
for (const n of ['Kalina Kacza', 'Jakub Stolarz']) {
  await api('POST', `/api/collections/${chlopak}/contributions`, { studentId: ids[n], amount: 2 });
}
// 3. Active trip without Alicja; six paid in full, two partly.
const trip = (await api('POST', '/api/collections', { title: 'Wycieczka do ZOO',
  description: 'Wyjazd do ZOO w Krakowie, 15 października. Bilety i autokar.', baseAmountPerStudent: 45,
  studentIds: all.filter((id) => id !== ids['Alicja Krawczyk']) })).id;
for (const [n, amount] of [['Jakub Stolarz', 45], ['Błażej Jezuit', 45], ['Michał Jarosz', 45], ['Ernest Paluch', 45],
  ['Ignacy Sitek', 45], ['Magdalena Woś', 45], ['Kalina Kacza', 18], ['Artur Zaskalski', 20]]) {
  await api('POST', `/api/collections/${trip}/contributions`, { studentId: ids[n], amount });
}

await context.storageState({ path: `${OUT}/state-treasurer.json` });
const parent = await login(browser, 'rodzic1');
await parent.context.storageState({ path: `${OUT}/state-parent.json` });
writeFileSync(`${OUT}/ids.json`, JSON.stringify({ ids, katechizm, chlopak, trip }, null, 2));
await browser.close();
console.log('seeded');
