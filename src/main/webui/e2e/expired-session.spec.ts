import { test, expect } from '@playwright/test';
import { seedTreasurer } from './seed';
import { Api, LoginPage, TreasurerPanel } from './pages';

/**
 * Start ("/") is public, so it must keep working when the browser still holds a token the
 * backend won't accept. Real bug: after a session ran out while the app was closed, the
 * stale ID token was still sent with /api/public/overview, Quarkus rejected the whole
 * request with 401 (it validates any bearer token it's given, even on a permitted path), and
 * Start showed no collections and no payment details until the user logged in again.
 * See core/auth.interceptor.ts.
 */
test.describe('public overview with a stale session', () => {
  let api: Api | undefined;
  let studentId: string | undefined;

  test.afterEach(async () => {
    if (api && studentId) {
      await api.deleteStudent(studentId);
    }
  });

  test('an expired or rejected token never hides the public collections and payment details', async ({
    page,
    request,
    baseURL,
  }) => {
    const unique = Date.now();
    const seeded = seedTreasurer('skarbnik@example.com', 'Jarek', 'Skarbnik', 'Jasio', `Wygasly${unique}`);
    studentId = seeded.studentId;

    await new LoginPage(page).loginAs('skarbnik', 'skarbnik');
    const token = await page.evaluate(() => localStorage.getItem('id_token'));
    api = new Api(request, baseURL!, token!);

    const treasurer = new TreasurerPanel(page);
    await treasurer.goto();
    await treasurer.setPaymentInfo('11 2222 3333 4444 5555 6666 7777', '600 700 800');
    const title = `Sesja wygasła ${unique}`;
    await treasurer.createCollection(title, 'Test wygasłej sesji', '10');

    // 1. The session ran out: the token is still stored, but expired.
    await page.evaluate(() => {
      const past = String(Date.now() - 60 * 60 * 1000);
      localStorage.setItem('id_token_expires_at', past);
      localStorage.setItem('expires_at', past);
    });
    await page.goto('/');
    await expect(page.getByTestId('payment-info')).toContainText('600 700 800');
    await expect(page.locator(`[data-testid="collection-sheet"][data-title="${title}"]`)).toBeVisible();

    // 2. A token the app still believes is valid, but the server rejects (revoked, tampered).
    await page.evaluate(() => {
      const future = String(Date.now() + 60 * 60 * 1000);
      localStorage.setItem('id_token', 'eyJhbGciOiJSUzI1NiJ9.eyJleHAiOjF9.invalid');
      localStorage.setItem('id_token_expires_at', future);
    });
    await page.goto('/');
    await expect(page.getByTestId('payment-info')).toContainText('600 700 800');
    await expect(page.locator(`[data-testid="collection-sheet"][data-title="${title}"]`)).toBeVisible();
  });
});
