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
    // The whole point of requirement 1: Zosia has no row at all, not a 0 zł one.
    await trip.requirements.expectAbsent(zosiaFullName);
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
    // Gone from the breakdown entirely - not a 0 zł / cancelled row.
    await trip.requirements.expectAbsent(jasioFullName);
    await trip.requirements.expectAbsent(zosiaFullName);

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
});
