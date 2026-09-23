import { test, expect } from '@playwright/test';
import { seedTreasurer } from './seed';
import { Api, CollectionDetailsPage, Dashboard, LoginPage, PiggyBankPage, TreasurerPanel } from './pages';

/**
 * Not every collection is "everyone in the class owes money" - a class trip the whole class
 * is invited to, but where the treasurer already knows one student can't come (or a student
 * who was in it falls ill and drops out partway through), needs two things a straight
 * "one requirement per student" collection doesn't:
 *
 * 1. **Exclude a student when creating the collection** - the "who's in this collection"
 *    checklist in TreasurerPanel starts with every student checked; unchecking one means
 *    that student gets no `ContributionRequirement` at all for this collection, not a 0 zł
 *    one (see backend `CreateCollectionUseCase`'s javadoc for why that distinction matters -
 *    a 0 zł requirement still shows up as "you owe nothing, but you're on this list").
 * 2. **Remove a student from an already-ACTIVE collection** - a trip participant who drops
 *    out after paying gets their contribution refunded straight back to their piggy bank,
 *    and stops counting towards this collection's math altogether (their
 *    `ContributionRequirement` and every `Contribution` they made to it are deleted, not
 *    just zeroed - see backend `RemoveStudentFromCollectionUseCase`).
 *
 * One collection, one story: Zosia is left off the trip from the start (requirement 1);
 * Jasio pays in, then has to drop out and gets refunded (requirement 2); the collection
 * still settles cleanly afterwards with nobody's stale contribution left in the math.
 */
test.describe('collection membership: exclude a student, or remove one mid-collection', () => {
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

  test('a student left off a collection never appears in it; a student removed mid-collection is refunded', async ({
    page,
    request,
    baseURL,
  }) => {
    const unique = Date.now();
    const studentLastName = `Wycieczkowa${unique}`;
    const jasioFullName = 'Jasio Skarbnik';
    const zosiaFullName = `Zosia ${studentLastName}`;

    const login = new LoginPage(page);
    const treasurer = new TreasurerPanel(page);
    const dashboard = new Dashboard(page);
    const piggyBank = new PiggyBankPage(page);

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
      firstName: 'Piotr',
      lastName: studentLastName,
      email: `piotr.${unique}@example.com`,
      expectedSenderName: `Piotr ${studentLastName}`,
    });
    zosiaStudentId = await api.studentIdByLastName(studentLastName);

    // Baseline, checked before any money moves - makes the later "+8 zł refund" assertion
    // meaningful rather than coincidental.
    await piggyBank.goto(jasioStudentId);
    await piggyBank.expectBalance('0');

    // ============================================================================
    // Requirement 1: uncheck Zosia before creating the collection - she's invited (every
    // student is), but the treasurer already knows she isn't coming.
    // ============================================================================
    const tripTitle = `Wycieczka do zoo ${unique}`;
    await treasurer.goto();
    await treasurer.excludeStudentFromNewCollection(zosiaFullName);
    await treasurer.createCollection(tripTitle, 'Każdy uczestnik płaci tyle samo', '8');

    await dashboard.goto();
    await dashboard.openCollection(tripTitle);
    const trip = new CollectionDetailsPage(page);
    await trip.requirements.expectRequiredAmount(jasioFullName, '8');
    await trip.requirements.expectStatus(jasioFullName, 'Do zapłaty');
    // The whole point of requirement 1: Zosia has no real requirement, shown as a distinct
    // greyed-out "not included" row (not a 0 zł one) - see the roster-merge test further
    // down for the dedicated add-her-back coverage.
    await trip.requirements.expectNotIncluded(zosiaFullName);
    // Capture the id now, only after the assertions above have proven the SPA navigation
    // from dashboard.openCollection has actually landed - `trip.id` reads the CURRENT page
    // URL live (see its getter), which would otherwise still be "/dashboard" (openCollection
    // clicking a routerLink doesn't wait for Angular's client-side route change to finish).
    // Must not be read again after navigating elsewhere below (to the piggy bank page and
    // back).
    const tripId = trip.id;

    // ============================================================================
    // Requirement 2: Jasio pays in, then falls ill and can't go - the treasurer removes
    // him from the still-ACTIVE collection, which must refund his 8 zł to his piggy bank.
    // ============================================================================
    await api.recordContribution(tripId, jasioStudentId, 8);
    await trip.reload();
    await trip.requirements.expectPaidAmount(jasioFullName, '8');
    await trip.requirements.expectStatus(jasioFullName, 'Zapłacone');

    await trip.removeStudent(jasioFullName);
    // No real requirement left for either of them - both now shown as "not included" greyed
    // rows (the collection is still ACTIVE), not gone from the table or a 0 zł/cancelled row.
    await trip.requirements.expectNotIncluded(jasioFullName);
    await trip.requirements.expectNotIncluded(zosiaFullName);

    await piggyBank.goto(jasioStudentId);
    await piggyBank.expectBalance('8');
    await piggyBank.activityLog.containsEntry(tripTitle);
    await piggyBank.activityLog.containsEntry('8 zł');

    // Nobody is left in the collection at all - it must still settle cleanly (0 zł
    // contributed, 0 zł spent), proving the removed student's contribution was actually
    // deleted rather than merely hidden (a lingering Contribution would otherwise still be
    // summed by SettlementPolicy the moment this runs).
    await dashboard.goto();
    await dashboard.openCollection(tripTitle);
    const tripAfterRemoval = new CollectionDetailsPage(page);
    await tripAfterRemoval.settle('0');
    await tripAfterRemoval.expectStatus('SETTLED');
  });

  /**
   * The other half of "excluding a student means their money is never touched" - see
   * CollectionService.createCollection's own comment: the automatic piggy-bank sweep only
   * ever runs for students actually included in the collection, so an excluded student's
   * balance must survive completely unchanged even when it would have been more than enough
   * to cover the ask. And the mirror case for requirement 2 above: a student who was covered
   * automatically (not via a manual/bank contribution) still gets a real refund + ledger
   * entry if removed mid-collection, because the sweep is a real Contribution now, not a
   * silent discount with nothing to refund (see ContributionRequirement's javadoc; fixed
   * 2026-09-23 after a real collection left 13 of 16 students' "already covered" shares
   * completely untouched with no audit trail at all).
   */
  test('an automatic piggy-bank sweep at creation is refunded and logged on removal; an excluded student is never touched', async ({
    page,
    request,
    baseURL,
  }) => {
    const unique = Date.now();
    const studentLastName = `Skarbonkowa${unique}`;
    const jasioFullName = 'Jasio Skarbnik';
    const zosiaFullName = `Zosia ${studentLastName}`;

    const login = new LoginPage(page);
    const treasurer = new TreasurerPanel(page);
    const dashboard = new Dashboard(page);
    const piggyBank = new PiggyBankPage(page);

    const seeded = seedTreasurer('skarbnik@example.com', 'Jarek', 'Skarbnik', 'Jasio', 'Skarbnik');
    jasioStudentId = seeded.studentId;

    await login.loginAs('skarbnik', 'skarbnik');
    const treasurerIdToken = await page.evaluate(() => localStorage.getItem('id_token'));
    expect(treasurerIdToken, 'expected an id_token in localStorage right after login').toBeTruthy();
    api = new Api(request, baseURL!, treasurerIdToken!);

    await treasurer.goto();
    const zosia = await treasurer.addStudent('Zosia', studentLastName);
    await zosia.addParent({
      firstName: 'Ola',
      lastName: studentLastName,
      email: `ola.${unique}@example.com`,
      expectedSenderName: `Ola ${studentLastName}`,
    });
    zosiaStudentId = await api.studentIdByLastName(studentLastName);

    // Both students already have exactly the base amount saved up, BEFORE the collection
    // exists - Jasio is included, Zosia is excluded from the start.
    await api.creditPiggyBank(jasioStudentId, 8);
    await api.creditPiggyBank(zosiaStudentId, 8);

    const collectionTitle = `Składka na klasę ${unique}`;
    await treasurer.goto();
    await treasurer.excludeStudentFromNewCollection(zosiaFullName);
    await treasurer.createCollection(collectionTitle, 'Test automatycznego pokrycia ze skarbonki', '8');

    // --- Zosia (excluded): her 8 zł is completely untouched - no debit, no sweep-related
    //     log entry. Her ledger isn't EMPTY (crediting her 8 zł above already wrote one
    //     PIGGY_BANK_CREDITED entry for that) - what matters is there's no SECOND entry
    //     referencing this collection, and the balance never moved.
    await piggyBank.goto(zosiaStudentId);
    await piggyBank.expectBalance('8');
    await expect(page.locator('.timeline li')).toHaveCount(1);
    await expect(page.locator('.timeline')).not.toContainText('zbiórkę');

    // --- Jasio (included): his 8 zł was swept in immediately, as a real operation ---
    await dashboard.goto();
    await dashboard.openCollection(collectionTitle);
    const collection = new CollectionDetailsPage(page);
    await collection.requirements.expectRequiredAmount(jasioFullName, '8');
    await collection.requirements.expectPaidAmount(jasioFullName, '8');
    await collection.requirements.expectStatus(jasioFullName, 'Zapłacone');
    await collection.requirements.expectNotIncluded(zosiaFullName);
    const collectionId = collection.id;

    await piggyBank.goto(jasioStudentId);
    await piggyBank.expectBalance('0');
    await piggyBank.activityLog.containsEntry('8 zł');
    await piggyBank.activityLog.containsEntry('zbiórkę');

    // --- Removing Jasio refunds the swept amount and logs it - not just a manual payment's
    //     refund, exactly the same as requirement 2 in the test above ---
    await page.goto(`/collections/${collectionId}`);
    await collection.removeStudent(jasioFullName);
    await collection.requirements.expectNotIncluded(jasioFullName);

    await piggyBank.goto(jasioStudentId);
    await piggyBank.expectBalance('8');
    await piggyBank.activityLog.containsEntry(collectionTitle);
    await piggyBank.activityLog.containsEntry('8 zł');
  });

  /**
   * The other direction from requirement 2 above, and the feature the user asked for next:
   * a student left off a collection (or removed from it) isn't a dead end - the treasurer
   * can put them back from the very same table, shown greyed out with an "add back" action
   * instead of vanishing from view entirely. Adding them back re-runs the exact same
   * automatic piggy-bank sweep as collection creation (see CollectionService.addRequirementForStudent,
   * shared by both paths). Also covers the settle card's own live summary (paid-count,
   * collected, expected) and its actual-cost input auto-filling with what's actually been
   * collected, instead of defaulting to 0.
   */
  test('adding a student back sweeps their piggy bank like at creation; the settle card shows live totals and pre-fills the cost', async ({
    page,
    request,
    baseURL,
  }) => {
    const unique = Date.now();
    const studentLastName = `Dolaczana${unique}`;
    const jasioFullName = 'Jasio Skarbnik';
    const zosiaFullName = `Zosia ${studentLastName}`;

    const login = new LoginPage(page);
    const treasurer = new TreasurerPanel(page);
    const dashboard = new Dashboard(page);
    const piggyBank = new PiggyBankPage(page);

    const seeded = seedTreasurer('skarbnik@example.com', 'Jarek', 'Skarbnik', 'Jasio', 'Skarbnik');
    jasioStudentId = seeded.studentId;

    await login.loginAs('skarbnik', 'skarbnik');
    const treasurerIdToken = await page.evaluate(() => localStorage.getItem('id_token'));
    expect(treasurerIdToken, 'expected an id_token in localStorage right after login').toBeTruthy();
    api = new Api(request, baseURL!, treasurerIdToken!);

    await treasurer.goto();
    const zosia = await treasurer.addStudent('Zosia', studentLastName);
    await zosia.addParent({
      firstName: 'Ewa',
      lastName: studentLastName,
      email: `ewa.${unique}@example.com`,
      expectedSenderName: `Ewa ${studentLastName}`,
    });
    zosiaStudentId = await api.studentIdByLastName(studentLastName);

    // Both already have exactly the base amount saved up before the collection exists -
    // Jasio is included from the start (so his own sweep happens at creation, as a
    // baseline), Zosia is excluded so her sweep only happens later, via the "add back" button.
    await api.creditPiggyBank(jasioStudentId, 8);
    await api.creditPiggyBank(zosiaStudentId, 8);

    const collectionTitle = `Skladka klasowa ${unique}`;
    await treasurer.goto();
    await treasurer.excludeStudentFromNewCollection(zosiaFullName);
    await treasurer.createCollection(collectionTitle, 'Test dodania ucznia z powrotem', '8');

    await dashboard.goto();
    await dashboard.openCollection(collectionTitle);
    const collection = new CollectionDetailsPage(page);
    await collection.requirements.expectRequiredAmount(jasioFullName, '8');
    await collection.requirements.expectStatus(jasioFullName, 'Zapłacone');
    await collection.requirements.expectNotIncluded(zosiaFullName);
    // Captured now, before navigating away to the piggy bank page below - CollectionDetailsPage.id
    // reads the CURRENT page URL live (see its getter), so it must never be read again after
    // that navigation.
    const collectionId = collection.id;

    // Before adding her back: Zosia's 8 zł is completely untouched, and the settle card's
    // own summary only counts the one real (Jasio's) requirement.
    await collection.settleSummary.expectStudentsPaid(1, 1);
    await collection.settleSummary.expectTotalCollected('8');
    await collection.settleSummary.expectTotalExpected('8');
    await collection.settleSummary.expectActualCostPrefilled('8');

    await piggyBank.goto(zosiaStudentId);
    await piggyBank.expectBalance('8');
    await expect(page.locator('.timeline li')).toHaveCount(1);
    await expect(page.locator('.timeline')).not.toContainText('zbiórkę');

    // --- Put Zosia back - same sweep as at creation, no confirmation dialog needed ---
    await page.goto(`/collections/${collectionId}`);
    await collection.addStudentBack(zosiaFullName);
    await collection.requirements.expectRequiredAmount(zosiaFullName, '8');
    await collection.requirements.expectPaidAmount(zosiaFullName, '8');
    await collection.requirements.expectStatus(zosiaFullName, 'Zapłacone');

    await piggyBank.goto(zosiaStudentId);
    await piggyBank.expectBalance('0');
    await piggyBank.activityLog.containsEntry(collectionTitle);
    await piggyBank.activityLog.containsEntry('8 zł');

    // --- The settle card's summary now reflects both students ---
    await page.goto(`/collections/${collectionId}`);
    await collection.settleSummary.expectStudentsPaid(2, 2);
    await collection.settleSummary.expectTotalCollected('16');
    await collection.settleSummary.expectTotalExpected('16');
    await collection.settleSummary.expectActualCostPrefilled('16');
  });
});
