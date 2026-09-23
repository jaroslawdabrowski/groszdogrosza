import { test, expect } from '@playwright/test';
import { seedTreasurer } from './seed';
import {
  Api,
  AppShell,
  CollectionDetailsPage,
  Dashboard,
  GlobalLedgerPage,
  LoginPage,
  PiggyBankPage,
  TreasurerPanel,
} from './pages';

/**
 * The main money flow, end to end, against a real running app (see e2e/README.md for how to
 * run it) - not mocked, not a unit test. One long sequential test rather than many small
 * ones: each step depends on state the previous step created (a collection to pay into, a
 * parent account to log in as, a piggy bank balance carried from one collection into the
 * next), so splitting it up would just mean re-doing setup per test for no real isolation
 * benefit. See `e2e/pages.ts` for the Page Object Model this spec is written against.
 *
 * Money is "paid in" via the treasurer directly recording a contribution
 * (`POST /api/collections/{id}/contributions`, see `Api.recordContribution`) rather than
 * through a UI button, because there isn't one yet (see CLAUDE.md's "No UI to manually
 * record a contribution" TODO) - and because a real parent payment normally arrives via the
 * automatic bank-statement pipeline (`BankStatementProcessingService`), not something a
 * parent clicks in this app at all. **That pipeline's own money-moving mechanics - crediting
 * the piggy bank first, then sweeping it against active requirements
 * (`ContributionAllocationPolicy`) - are NOT exercised here**: they're covered by
 * `PaymentMatchingPolicyTest`/`ContributionAllocationPolicyTest` and were manually verified
 * against a mocked mailbox (see CLAUDE.md, "Verified end-to-end locally"), and wiring up a
 * real/mock IMAP server for this suite would need the dev server itself to be started with
 * IMAP config - a bigger change than this suite's own bootstrap. What this test's settlement
 * scenarios below verify instead is `SettlementPolicy`'s surplus-crediting math (contributed
 * minus actual cost, credited back to the piggy bank of whoever contributed) - the same
 * arithmetic, reached through the manual-contribution endpoint instead of the automatic
 * sweep. The two worked examples below use the exact numbers from the app's own spec: "do
 * skarbonki poszło 50 zł, a zbiórka była 10 zł, więc w skarbonce zostało 40" (collection A),
 * then "zbiórka wyszła po 8 zł na głowę zamiast 10, więc 2 zł wraca do skarbonki - jest 42"
 * (collection B).
 *
 * Cleans up the students it creates in `afterEach` (using a treasurer token captured during
 * the test, even after the test itself logs out and in as a different user) - both the
 * seeded treasurer's own child and the one created through the UI are deleted via the real
 * `DELETE /api/students/{id}` (which cascades to the linked Parent row - see
 * `DeleteStudentUseCase`'s javadoc). This isn't just tidiness: `Parent.email` isn't unique,
 * and `AuthorizationSupport` resolves "who am I" via a full-table scan's `.findFirst()` - a
 * leftover Parent row from a previous run sharing the same dev-user email as this run can
 * non-deterministically win that scan and make the *next* run assert against stale data
 * instead of what it just created. Confirmed the hard way: the second consecutive run
 * without cleanup failed on a stale-student mismatch before this hook was added.
 */
test.describe('main flow: collection → payment → settlement → both logins see it', () => {
  let api: Api | undefined;
  let treasurerKidStudentId: string | undefined;
  let kasiaStudentId: string | undefined;

  test.afterEach(async () => {
    if (!api) {
      return;
    }
    if (kasiaStudentId) {
      await api.deleteStudent(kasiaStudentId);
    }
    if (treasurerKidStudentId) {
      await api.deleteStudent(treasurerKidStudentId);
    }
  });

  test('treasurer creates collections, payments are recorded, settlement math is right, both logins see it', async ({
    page,
    request,
    baseURL,
  }) => {
    const unique = Date.now();
    const studentLastName = `Testowa${unique}`;
    const kasiaFullName = `Kasia ${studentLastName}`;
    const jasioFullName = 'Jasio Skarbnik';

    const login = new LoginPage(page);
    const app = new AppShell(page);
    const treasurer = new TreasurerPanel(page);
    const dashboard = new Dashboard(page);
    const globalLedger = new GlobalLedgerPage(page);
    const piggyBank = new PiggyBankPage(page);

    // Must be the exact email keycloak-realm.json's "skarbnik" dev user logs in as -
    // AuthorizationSupport resolves the caller's own Parent record by matching this email
    // against the OIDC token's email claim, not by any stored subject id.
    const seeded = seedTreasurer('skarbnik@example.com', 'Jarek', 'Skarbnik', 'Jasio', 'Skarbnik');
    treasurerKidStudentId = seeded.studentId;

    await login.loginAs('skarbnik', 'skarbnik');
    const treasurerIdToken = await page.evaluate(() => localStorage.getItem('id_token'));
    expect(treasurerIdToken, 'expected an id_token in localStorage right after login').toBeTruthy();
    api = new Api(request, baseURL!, treasurerIdToken!);

    await treasurer.goto();
    await treasurer.expectStudentVisible(jasioFullName);

    // --- Add a second student with a parent, via the real UI ---
    const kasia = await treasurer.addStudent('Kasia', studentLastName);
    await kasia.addParent({
      firstName: 'Anna',
      lastName: studentLastName,
      // Must match keycloak-realm.json's "rodzic1" dev user's real email exactly - same
      // email-claim-matching rule as the treasurer seeding above.
      email: 'anna.testowa@example.com',
      expectedSenderName: `Anna ${studentLastName}`,
    });
    await kasia.expectParentVisible('anna.testowa@example.com');

    kasiaStudentId = await api.studentIdByLastName(studentLastName);

    // --- Payment info (shown on the public, unauthenticated page) ---
    await treasurer.setPaymentInfo('11 2222 3333 4444 5555 6666 7777', '600 700 800');

    // ============================================================================
    // Collection A: one overpaying student, settled at exactly the requested amount.
    // 50 zł paid in, 10 zł actually needed -> the whole 40 zł surplus goes to Kasia's
    // piggy bank. Also checks the requirement's initial status before any payment lands.
    // ============================================================================
    const collectionATitle = `Prezent testowy ${unique}`;
    await treasurer.createCollection(collectionATitle, 'Zbiórka utworzona przez test end-to-end', '10');

    await dashboard.goto();
    await dashboard.openCollection(collectionATitle);
    const collectionA = new CollectionDetailsPage(page);
    await collectionA.requirements.expectRequiredAmount(kasiaFullName, '10');
    await collectionA.requirements.expectPaidAmount(kasiaFullName, '0');
    await collectionA.requirements.expectStatus(kasiaFullName, 'Do zapłaty');

    await api.recordContribution(collectionA.id, kasiaStudentId, 50);
    await collectionA.reload();
    await collectionA.requirements.expectPaidAmount(kasiaFullName, '50');
    await collectionA.requirements.expectStatus(kasiaFullName, 'Nadpłacone');
    await collectionA.contributions.containsEntry('50 zł');

    await collectionA.settle('10');
    await collectionA.settlementResult.containsEntry(`${kasiaFullName}: +40 zł`);
    await collectionA.expectStatus('SETTLED');

    await piggyBank.goto(kasiaStudentId);
    await piggyBank.expectStudentName(kasiaFullName);
    await piggyBank.expectBalance('40');
    await piggyBank.activityLog.isVisible();
    await piggyBank.activityLog.containsEntry('40 zł');

    // ============================================================================
    // Collection B: two students, actual cost comes in under the 10 zł ask - "wyszło po
    // 8 zł na głowę" - each contributor's 2 zł surplus is credited back. Also exercises the
    // OTHER piggy-bank interaction: Kasia's now-40-zł balance already covers this
    // collection's 10 zł base amount on its own, so it's swept in immediately at creation -
    // a real piggy bank debit, a real Contribution, and a real ledger entry (see
    // CollectionService.createCollection's own comment), not just a silently-discounted
    // requirement with no money movement or audit trail (the bug this was fixed from,
    // 2026-09-23 - see ContributionRequirement's javadoc). requiredAmount stays the nominal
    // 10 zł; it's paidAmount that shows the sweep.
    // ============================================================================
    await treasurer.goto();
    const collectionBTitle = `Wycieczka testowa ${unique}`;
    await treasurer.createCollection(collectionBTitle, 'Druga zbiórka - test rozliczenia po niższym koszcie', '10');

    await dashboard.goto();
    await dashboard.openCollection(collectionBTitle);
    const collectionB = new CollectionDetailsPage(page);
    await collectionB.requirements.expectRequiredAmount(kasiaFullName, '10');
    await collectionB.requirements.expectPaidAmount(kasiaFullName, '10');
    await collectionB.requirements.expectStatus(kasiaFullName, 'Zapłacone');
    await collectionB.requirements.expectRequiredAmount(jasioFullName, '10');
    await collectionB.requirements.expectStatus(jasioFullName, 'Do zapłaty');
    // Captured now, while still on the collection's own page - collection.id parses the
    // CURRENT page URL live (see its getter), which stops being /collections/<id> the
    // moment we navigate to the piggy bank page below.
    const collectionBId = collectionB.id;

    // The sweep is visible on Kasia's own piggy bank straight away, not just on the
    // collection's requirements table - the whole point of booking it as a real operation.
    await piggyBank.goto(kasiaStudentId);
    await piggyBank.expectBalance('30'); // 40 - the 10 zł just swept into collection B
    await piggyBank.activityLog.containsEntry('10 zł');
    await piggyBank.activityLog.containsEntry('zbiórkę');
    await page.goto(`/collections/${collectionBId}`);

    // Jasio still genuinely owes the full 10 zł - he had no prior balance.
    await api.recordContribution(collectionBId, treasurerKidStudentId, 10);
    await collectionB.reload();
    await collectionB.requirements.expectStatus(jasioFullName, 'Zapłacone');

    // 20 zł contributed in total (Kasia's swept 10 + Jasio's paid 10), the trip actually
    // cost 16 zł -> 4 zł surplus, split evenly two ways (no odd grosz to worry about here -
    // see SettlementPolicyTest for that case). Kasia's swept contribution counting toward
    // this split is exactly the point of the fix - previously she'd have been silently
    // excluded from any surplus since no Contribution record ever existed for her.
    await collectionB.settle('16');
    await collectionB.settlementResult.containsEntry(`${kasiaFullName}: +2 zł`);
    await collectionB.settlementResult.containsEntry(`${jasioFullName}: +2 zł`);
    await collectionB.expectStatus('SETTLED');

    await piggyBank.goto(kasiaStudentId);
    await piggyBank.expectBalance('32'); // 30 after the sweep + 2 zł settlement surplus

    // --- Treasurer-only global ledger shows both students' events ---
    await globalLedger.goto();
    await globalLedger.activityLog.containsEntry(kasiaFullName);
    await globalLedger.activityLog.containsEntry(jasioFullName);
    await globalLedger.activityLog.containsEntry('50 zł');

    await app.logout();

    // --- The parent logs in and sees their own child's collections and piggy bank, not
    // the treasurer's kid's ---
    await login.loginAs('rodzic1', 'rodzic1');

    await dashboard.goto();
    await dashboard.expectCollectionVisible(collectionATitle);
    await dashboard.expectCollectionVisible(collectionBTitle);

    await app.openMyPiggyBank();
    await piggyBank.expectStudentName(kasiaFullName);
    await piggyBank.expectBalance('32');
    await piggyBank.activityLog.containsEntry('Wpłata');
  });
});
