import { test, expect } from '@playwright/test';
import { seedTreasurer } from './seed';
import { Api, CollectionDetailsPage, Dashboard, LoginPage, PiggyBankPage, TreasurerPanel } from './pages';

/**
 * A student who already has money in their piggy bank when a new collection is created
 * shouldn't be asked for the full base amount again - CollectionService.createCollection
 * immediately sweeps whatever the existing balance can cover into a real Contribution,
 * exactly like an incoming bank transfer would (see ContributionRequirement's and
 * createCollection's own javadoc). This is the "partial coverage" case: the existing balance
 * covers *some* but not all of the ask - the student's own worked example: 10 zł already in
 * the piggy bank, a 30 zł collection -> the 10 zł is swept in immediately (piggy bank debited
 * to 0, a real Contribution + ledger entry recorded), 20 zł still genuinely owed, status
 * PENDING ("Do zapłaty") until a real payment covers the rest. Fixed 2026-09-23 after a real
 * collection silently marked students "PAID" with zero money movement and zero ledger entry
 * whenever their piggy bank fully covered the ask - see CollectionProgressResponseTest for
 * the aggregate-totals half of that same bug.
 */
test.describe('piggy bank balance partially covers a new collection', () => {
  let api: Api | undefined;
  let studentId: string | undefined;

  test.afterEach(async () => {
    if (api && studentId) {
      await api.deleteStudent(studentId);
    }
  });

  test('a student with 10 zł already saved has it swept in immediately, and owes only the 20 zł difference', async ({
    page,
    request,
    baseURL,
  }) => {
    const unique = Date.now();
    const studentFullName = 'Jasio Skarbnik';

    const login = new LoginPage(page);
    const treasurer = new TreasurerPanel(page);
    const dashboard = new Dashboard(page);
    const piggyBank = new PiggyBankPage(page);

    // Must be the exact email keycloak-realm.json's "skarbnik" dev user logs in as - see
    // main-flow.spec.ts's class comment for the same rule.
    const seeded = seedTreasurer('skarbnik@example.com', 'Jarek', 'Skarbnik', 'Jasio', 'Skarbnik');
    studentId = seeded.studentId;

    await login.loginAs('skarbnik', 'skarbnik');
    const treasurerIdToken = await page.evaluate(() => localStorage.getItem('id_token'));
    expect(treasurerIdToken, 'expected an id_token in localStorage right after login').toBeTruthy();
    api = new Api(request, baseURL!, treasurerIdToken!);

    // Jasio already has 10 zł saved up, from before this collection ever existed.
    await api.creditPiggyBank(studentId, 10);

    const collectionTitle = `Wpłata na klasę ${unique}`;
    await treasurer.goto();
    await treasurer.createCollection(collectionTitle, 'Test częściowego pokrycia ze skarbonki', '30');

    await dashboard.goto();
    await dashboard.openCollection(collectionTitle);
    const collection = new CollectionDetailsPage(page);

    // Required stays the nominal 30 zł (not discounted) - the 10 zł already saved shows up
    // as a real "paid" amount instead, exactly like any other contribution would.
    await collection.requirements.expectRequiredAmount(studentFullName, '30');
    await collection.requirements.expectPaidAmount(studentFullName, '10');
    await collection.requirements.expectStatus(studentFullName, 'Do zapłaty');
    // Capture the id now, while still on the collection's own page - `collection.id` reads
    // the CURRENT page URL live, so it must not be read again after navigating to the piggy
    // bank page below (see main-flow.spec.ts/collection-membership.spec.ts for the same gotcha).
    const collectionId = collection.id;

    // The pre-existing 10 zł was genuinely swept out of the piggy bank at creation time, not
    // just silently discounted - both the balance and the ledger reflect a real money move.
    await piggyBank.goto(studentId);
    await piggyBank.expectBalance('0');
    await piggyBank.activityLog.containsEntry('10 zł');
    await piggyBank.activityLog.containsEntry('zbiórkę');

    // Only once a payment covering the remaining 20 zł is recorded against this collection
    // does the status flip to PAID.
    await api.recordContribution(collectionId, studentId, 20);
    // We navigated to the piggy bank page above - go back to the collection (not
    // collection.reload(), which would just reload whatever page we're currently on).
    await page.goto(`/collections/${collectionId}`);
    await collection.requirements.expectPaidAmount(studentFullName, '30');
    await collection.requirements.expectStatus(studentFullName, 'Zapłacone');

    // Recording a contribution never touches the piggy bank on its own (only settling a
    // collection credits a leftover back to it) - still exactly 0, the sweep already happened.
    await piggyBank.goto(studentId);
    await piggyBank.expectBalance('0');
  });
});
