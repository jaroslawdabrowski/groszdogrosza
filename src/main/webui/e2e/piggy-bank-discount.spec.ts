import { test, expect } from '@playwright/test';
import { seedTreasurer } from './seed';
import { Api, CollectionDetailsPage, Dashboard, LoginPage, PiggyBankPage, TreasurerPanel } from './pages';

/**
 * A student who already has money in their piggy bank when a new collection is created
 * shouldn't be asked for the full base amount again - CollectionService.createCollection
 * computes `requiredAmount = baseAmountPerStudent - piggyBankBalance` (floored at zero) at
 * the moment the collection is created. This is the "partial discount" case: the existing
 * balance covers *some* but not all of the ask, so the requirement is reduced but not zero -
 * the student's own worked example: 10 zł already in the piggy bank, a 30 zł collection ->
 * 20 zł still owed, status PENDING ("Do zapłaty") from the very start, not PAID. It only
 * flips to PAID once contributions actually recorded against *this collection* reach that
 * remaining 20 zł - the pre-existing balance itself is never touched or re-checked again
 * (see ContributionRequirement's own javadoc: fixed at creation time on purpose). The other
 * branch of this same logic - balance covers the ask *entirely*, required = 0 immediately -
 * is already covered by main-flow.spec.ts's Collection B; this is the partial-discount
 * branch, previously untested end to end.
 */
test.describe('piggy bank balance partially covers a new collection', () => {
  let api: Api | undefined;
  let studentId: string | undefined;

  test.afterEach(async () => {
    if (api && studentId) {
      await api.deleteStudent(studentId);
    }
  });

  test('a student with 10 zł already saved owes only the 20 zł difference on a 30 zł collection', async ({
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
    const treasurerIdToken = await page.evaluate(() => sessionStorage.getItem('id_token'));
    expect(treasurerIdToken, 'expected an id_token in sessionStorage right after login').toBeTruthy();
    api = new Api(request, baseURL!, treasurerIdToken!);

    // Jasio already has 10 zł saved up, from before this collection ever existed.
    await api.creditPiggyBank(studentId, 10);

    const collectionTitle = `Wpłata na klasę ${unique}`;
    await treasurer.goto();
    await treasurer.createCollection(collectionTitle, 'Test częściowego pokrycia ze skarbonki', '30');

    await dashboard.goto();
    await dashboard.openCollection(collectionTitle);
    const collection = new CollectionDetailsPage(page);

    // 30 zł ask - 10 zł already saved = 20 zł still owed, and PENDING (not PAID) because
    // 20 zł is not zero - this is the whole point of the test.
    await collection.requirements.expectRequiredAmount(studentFullName, '20');
    await collection.requirements.expectPaidAmount(studentFullName, '0');
    await collection.requirements.expectStatus(studentFullName, 'Do zapłaty');
    // Capture the id now, while still on the collection's own page - `collection.id` reads
    // the CURRENT page URL live, so it must not be read again after navigating to the piggy
    // bank page below (see main-flow.spec.ts/collection-membership.spec.ts for the same gotcha).
    const collectionId = collection.id;

    // The pre-existing 10 zł is a one-time discount applied to the requirement, not money
    // that moved anywhere - the piggy bank itself is untouched until something is actually
    // paid in and swept/recorded against this specific collection.
    await piggyBank.goto(studentId);
    await piggyBank.expectBalance('10');

    // Only once a payment covering exactly the remaining 20 zł is recorded against this
    // collection does the status flip to PAID.
    await api.recordContribution(collectionId, studentId, 20);
    // We navigated to the piggy bank page above - go back to the collection (not
    // collection.reload(), which would just reload whatever page we're currently on).
    await page.goto(`/collections/${collectionId}`);
    await collection.requirements.expectPaidAmount(studentFullName, '20');
    await collection.requirements.expectStatus(studentFullName, 'Zapłacone');

    // Recording a contribution never touches the piggy bank on its own (only settling a
    // collection credits a leftover back to it) - still exactly the original 10 zł.
    await piggyBank.goto(studentId);
    await piggyBank.expectBalance('10');
  });
});
