import { test, expect } from '@playwright/test';
import { seedTreasurer } from './seed';
import { Api, AppShell, Dashboard, LoginPage, PublicOverviewPage, TreasurerPanel } from './pages';

/**
 * "Does MY child take part in this collection, and have they paid" - a small icon badge on
 * a collection's card (see MyStudentStatusBadge), shown on both the Dashboard (authGuard'd,
 * `/dashboard`) and the unauthenticated homepage (`/`, PublicOverview) - the user explicitly
 * asked for it on both. Never another family's status - each login only ever resolves its
 * OWN Parent -> Student link server-side (see backend CollectionResponse.myStudentStatus's
 * javadoc), which this test proves by checking two different parents' logins against the
 * exact same collection see two different icons.
 *
 * One collection, three vantage points: Kasia is included and starts unpaid (icon = "not
 * paid"), then pays and the icon flips to "paid"; Zosia is excluded from the same collection
 * from the start (icon = "not participating"); an anonymous visitor to the public homepage
 * sees no icon at all, for either child, since there's no login to resolve "my child" from.
 */
test.describe('collection widgets: "does my child take part / have they paid" badge', () => {
  let api: Api | undefined;
  let treasurerKidStudentId: string | undefined;
  let kasiaStudentId: string | undefined;
  let zosiaStudentId: string | undefined;

  test.afterEach(async () => {
    if (!api) {
      return;
    }
    if (kasiaStudentId) {
      await api.deleteStudent(kasiaStudentId);
    }
    if (zosiaStudentId) {
      await api.deleteStudent(zosiaStudentId);
    }
    if (treasurerKidStudentId) {
      await api.deleteStudent(treasurerKidStudentId);
    }
  });

  test('two different parent logins see two different badges on the same collection; an anonymous visitor sees none', async ({
    page,
    request,
    baseURL,
  }) => {
    // Three real logins plus several full page loads each - heavier than this suite's usual
    // budget, and the dev DB accumulates ACTIVE collections across every previous spec run
    // (most specs never settle/delete the collections they create), which makes every
    // GET /api/collections / GET /api/public/overview call progressively slower over a long
    // local test session. The default 30s timeout was hit for exactly that reason once.
    test.setTimeout(60_000);

    const unique = Date.now();
    const studentLastName = `Znaczkowa${unique}`;
    const kasiaFullName = `Kasia ${studentLastName}`;
    const zosiaFullName = `Zosia ${studentLastName}`;

    const login = new LoginPage(page);
    const app = new AppShell(page);
    const treasurer = new TreasurerPanel(page);
    const dashboard = new Dashboard(page);
    const publicOverview = new PublicOverviewPage(page);

    const seeded = seedTreasurer('skarbnik@example.com', 'Jarek', 'Skarbnik', 'Jasio', 'Skarbnik');
    treasurerKidStudentId = seeded.studentId;

    await login.loginAs('skarbnik', 'skarbnik');
    const treasurerIdToken = await page.evaluate(() => localStorage.getItem('id_token'));
    expect(treasurerIdToken, 'expected an id_token in localStorage right after login').toBeTruthy();
    api = new Api(request, baseURL!, treasurerIdToken!);

    await treasurer.goto();
    const kasia = await treasurer.addStudent('Kasia', studentLastName);
    await kasia.addParent({
      firstName: 'Anna',
      lastName: studentLastName,
      // Must match keycloak-realm.json's "rodzic1" dev user's real email exactly.
      email: 'anna.testowa@example.com',
      expectedSenderName: `Anna ${studentLastName}`,
    });
    // Waits for the add-parent request to actually finish (and its form to close) before
    // moving on - addParent's page-object method only clicks submit, it doesn't wait for the
    // response. Without this, the next addStudent call below can race that still-in-flight
    // request and find TWO "Imię" fields on screen (Kasia's still-open add-parent form, plus
    // the page's own "add student" section) - hit this exact race while writing this test.
    await kasia.expectParentVisible('anna.testowa@example.com');
    kasiaStudentId = await api.studentIdByLastName(studentLastName);

    // Zosia needs her OWN, distinct lastName to resolve unambiguously via
    // studentIdByLastName - reuse Kasia's would collide.
    const zosiaLastName = `${studentLastName}Z`;
    const zosia = await treasurer.addStudent('Zosia', zosiaLastName);
    await zosia.addParent({
      firstName: 'Piotr',
      lastName: zosiaLastName,
      // Must match keycloak-realm.json's "rodzic2" dev user's real email exactly.
      email: 'piotr.testowy@example.com',
      expectedSenderName: `Piotr ${zosiaLastName}`,
    });
    await zosia.expectParentVisible('piotr.testowy@example.com');
    zosiaStudentId = await api.studentIdByLastName(zosiaLastName);

    const collectionTitle = `Znaczek testowy ${unique}`;
    await treasurer.goto();
    await treasurer.excludeStudentFromNewCollection(zosiaFullName);
    await treasurer.createCollection(collectionTitle, 'Test odznaki statusu dziecka', '10');

    await app.logout();

    // --- Kasia's parent (included, not yet paid): "not paid" icon, on both widgets ---
    await login.loginAs('rodzic1', 'rodzic1');
    await dashboard.goto();
    await dashboard.expectMyStudentStatus(collectionTitle, 'cancel');
    await publicOverview.goto();
    await publicOverview.expectMyStudentStatus(collectionTitle, 'cancel');

    // --- Record Kasia's payment via the treasurer's own (still-valid) token - a raw API
    //     call, not a UI action, so no login switch is needed here (see main-flow.spec.ts's
    //     class comment for the same "no UI to record a contribution yet" reasoning) - then
    //     re-check the SAME still-logged-in session now sees "paid" instead. ---
    const collectionId = await api.collectionIdByTitle(collectionTitle);
    await api.recordContribution(collectionId, kasiaStudentId, 10);

    await dashboard.goto();
    await dashboard.expectMyStudentStatus(collectionTitle, 'check_circle');
    await publicOverview.goto();
    await publicOverview.expectMyStudentStatus(collectionTitle, 'check_circle');
    await app.logout();

    // --- Zosia's parent (excluded from the start): "not participating" icon ---
    await login.loginAs('rodzic2', 'rodzic2');
    await dashboard.goto();
    await dashboard.expectMyStudentStatus(collectionTitle, 'do_not_disturb_on');
    await publicOverview.goto();
    await publicOverview.expectMyStudentStatus(collectionTitle, 'do_not_disturb_on');
    await app.logout();

    // --- An anonymous visitor to the public homepage: no badge at all, for either child ---
    await publicOverview.goto();
    await publicOverview.expectMyStudentStatusAbsent(collectionTitle);
  });
});
