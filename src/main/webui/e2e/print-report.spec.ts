import { test, expect } from '@playwright/test';
import { seedTreasurer } from './seed';
import { Api, CollectionDetailsPage, Dashboard, LoginPage, TreasurerPanel } from './pages';

/**
 * "Drukuj raport" on a collection's details page - a status report (who's paid, who
 * hasn't) the treasurer can print or save as a PDF, e.g. to hand over with cash to a
 * teacher. Treasurer-only by construction, not a separate check: the button only exists
 * inside the `isCollectionDetails` branch of the template, which the backend already
 * restricts to a treasurer (`CollectionResource.get` returns `CollectionDetailsResponse`
 * only when `authorizationSupport.isTreasurer(identity)` - a regular parent gets
 * `CollectionProgressResponse` instead, which carries no per-student breakdown to print in
 * the first place). This suite proves both ends of that: the button and print-only content
 * actually work for a treasurer, and a regular parent viewing the very same collection
 * never sees the button at all.
 */
test.describe('collection details: print report', () => {
  let api: Api | undefined;
  let jasioStudentId: string | undefined;
  let zosiaStudentId: string | undefined;

  test.afterEach(async () => {
    if (!api) {
      return;
    }
    if (zosiaStudentId) {
      await api.deleteStudent(zosiaStudentId);
    }
    if (jasioStudentId) {
      await api.deleteStudent(jasioStudentId);
    }
  });

  test('a treasurer can print a report; a regular parent never sees the button', async ({ page, request, baseURL }) => {
    const unique = Date.now();
    const studentLastName = `Drukowa${unique}`;
    const collectionTitle = `Prezent dla wychowawczyni ${unique}`;

    const login = new LoginPage(page);
    const treasurer = new TreasurerPanel(page);
    const dashboard = new Dashboard(page);

    // Must be the exact email keycloak-realm.json's "skarbnik" dev user logs in as - see
    // main-flow.spec.ts's class comment for the same rule.
    const seeded = seedTreasurer('skarbnik@example.com', 'Jarek', 'Skarbnik', 'Jasio', 'Skarbnik');
    jasioStudentId = seeded.studentId;

    await login.loginAs('skarbnik', 'skarbnik');
    const treasurerIdToken = await page.evaluate(() => localStorage.getItem('id_token'));
    expect(treasurerIdToken, 'expected an id_token in localStorage right after login').toBeTruthy();
    api = new Api(request, baseURL!, treasurerIdToken!);

    await treasurer.goto();
    const zosia = await treasurer.addStudent('Zosia', studentLastName);
    await zosia.addParent({
      firstName: 'Anna',
      lastName: studentLastName,
      // Must match keycloak-realm.json's "rodzic1" dev user's real email exactly - same
      // email-claim-matching rule as main-flow.spec.ts.
      email: 'anna.testowa@example.com',
      expectedSenderName: `Anna ${studentLastName}`,
    });
    zosiaStudentId = await api.studentIdByLastName(studentLastName);

    await treasurer.createCollection(collectionTitle, 'Zbiórka na koniec roku', '30');
    await dashboard.goto();
    await dashboard.openCollection(collectionTitle);
    const collection = new CollectionDetailsPage(page);
    await collection.requirements.expectRequiredAmount('Jasio Skarbnik', '30');
    const collectionId = collection.id;

    await api.recordContribution(collectionId, jasioStudentId, 30);
    await page.goto(`/collections/${collectionId}`);

    // --- Treasurer's view: the button exists, and print CSS actually does what it should ---
    await collection.expectPrintButtonVisible();

    await collection.enterPrintPreview();
    await collection.expectPrintOnlyContentVisible();
    await collection.expectInteractiveChromeHiddenForPrint();
    // The report's whole point: how much money is actually in hand.
    await expect(page.locator('.total-collected')).toContainText('30 zł');
    await collection.exitPrintPreview();

    // --- A regular parent viewing the exact same collection never sees the button ---
    await page.getByRole('button', { name: 'Log out' }).click();
    await page.waitForLoadState('networkidle');
    await login.loginAs('rodzic1', 'rodzic1');
    await page.goto(`/collections/${collectionId}`);
    await expect(page.locator('.percent')).toBeVisible(); // confirms we're on the real page, not a 404
    await collection.expectPrintButtonAbsent();
  });
});
