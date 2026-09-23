import { test, expect } from '@playwright/test';
import { seedTreasurer } from './seed';
import { Api, CollectionDetailsPage, LoginPage, PublicOverviewPage, TreasurerPanel } from './pages';

/**
 * The public homepage ("/") shows the same active-collection cards to everyone, logged in
 * or not - but only a logged-in visitor should be able to click through to the collection's
 * own detail page (the same one Dashboard's cards link to), since that route is behind
 * authGuard. An anonymous visitor's card must stay plain, non-interactive content - clicking
 * into a route that just bounces them to a login wall would be a worse experience than not
 * offering the click at all, and the whole point of this page is working without an account.
 */
test.describe('public overview: collection cards are clickable only when logged in', () => {
  let api: Api | undefined;
  let jasioStudentId: string | undefined;

  test.afterEach(async () => {
    if (api && jasioStudentId) {
      await api.deleteStudent(jasioStudentId);
    }
  });

  test('a logged-in visitor can open a collection from the homepage; an anonymous visitor cannot', async ({
    page,
    request,
    baseURL,
  }) => {
    const unique = Date.now();
    const collectionTitle = `Składka klasowa ${unique}`;

    const login = new LoginPage(page);
    const treasurer = new TreasurerPanel(page);
    const publicOverview = new PublicOverviewPage(page);

    // Must be the exact email keycloak-realm.json's "skarbnik" dev user logs in as - see
    // main-flow.spec.ts's class comment for the same rule.
    const seeded = seedTreasurer('skarbnik@example.com', 'Jarek', 'Skarbnik', 'Jasio', 'Skarbnik');
    jasioStudentId = seeded.studentId;

    await login.loginAs('skarbnik', 'skarbnik');
    const treasurerIdToken = await page.evaluate(() => localStorage.getItem('id_token'));
    expect(treasurerIdToken, 'expected an id_token in localStorage right after login').toBeTruthy();
    api = new Api(request, baseURL!, treasurerIdToken!);

    await treasurer.goto();
    await treasurer.createCollection(collectionTitle, 'Test klikalności na stronie głównej', '8');

    // --- Logged in: the homepage card opens the exact same detail page Dashboard's does ---
    await publicOverview.goto();
    await publicOverview.openCollection(collectionTitle);
    const collection = new CollectionDetailsPage(page);
    await collection.requirements.expectRequiredAmount('Jasio Skarbnik', '8');

    // --- Logged out: the same card on the same page is no longer a link at all ---
    await page.getByRole('button', { name: 'Log out' }).click();
    await page.waitForLoadState('networkidle');
    await publicOverview.goto();
    await publicOverview.expectCollectionNotClickable(collectionTitle);
  });
});
