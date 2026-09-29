import { test, expect, type Route } from '@playwright/test';

/**
 * The launch splash (#gg-splash, inline in index.html) covers the time Angular spends in its
 * app initializer before rendering anything - chiefly GET /api/auth-config, which is a Lambda
 * cold start in production. Holding that one request open is how these tests freeze the app
 * in its "still starting" state; no login or seeding needed.
 */
test.describe('launch splash', () => {
  test('shows while the app is starting, and is removed once the first page renders', async ({ page }) => {
    let release!: () => void;
    const released = new Promise<void>((resolve) => (release = resolve));
    await page.route('**/api/auth-config', async (route: Route) => {
      await released;
      await route.continue();
    });

    await page.goto('/');
    const splash = page.locator('#gg-splash');
    await expect(splash).toBeVisible();
    await expect(splash).toContainText('Grosz do Grosza');
    await expect(page.getByRole('button', { name: 'Spróbuj ponownie' })).toBeHidden();

    release();
    await expect(page.locator('app-public-overview')).toBeVisible();
    await expect(splash).toHaveCount(0);
  });

  test('turns into a retry message when the app cannot start (e.g. opened offline)', async ({ page }) => {
    await page.route('**/api/auth-config', (route) => route.abort());

    await page.goto('/');
    await expect(page.locator('#gg-splash')).toContainText('Nie udało się uruchomić aplikacji');
    await expect(page.getByRole('button', { name: 'Spróbuj ponownie' })).toBeVisible();
  });
});
