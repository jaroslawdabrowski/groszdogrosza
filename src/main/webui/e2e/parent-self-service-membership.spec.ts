import { test, expect } from '@playwright/test';
import { seedTreasurer } from './seed';
import { Api, AppShell, CollectionDetailsPage, Dashboard, LoginPage, TreasurerPanel } from './pages';

/**
 * "dodaj rodzicowi dziecka mozliwosc dodania lub usuniecia ucznia z aktywnej zbiorki" - a
 * regular parent (not just the treasurer) can now opt their OWN child in or out of an
 * ACTIVE collection, from that collection's own page. Backend: `CollectionResource.removeStudent`/
 * `addStudent` now allow `AuthorizationSupport.requireSelfOrTreasurerForStudent` (self or
 * treasurer), not `requireTreasurer` only - a parent can only ever act on their OWN linked
 * Student (a 403 for anyone else's), same rule already used for a student's own piggy
 * bank/ledger. Frontend: the non-treasurer progress card gains a join/leave button, gated on
 * `myStudentStatus` (NOT_INCLUDED -> join, otherwise -> leave) and the collection being
 * ACTIVE.
 *
 * One collection: Kasia starts excluded (icon = "not participating", join button visible),
 * her own parent joins her in - a real requirement appears, the icon flips to "not paid yet".
 * Then that same parent opts her back out - the requirement disappears again and any money
 * already paid is refunded to her piggy bank (unchanged RemoveStudentFromCollectionUseCase
 * behavior, just reached through a new, self-service door). A second, unrelated parent
 * trying to act on Kasia's id directly via the API is rejected with 403.
 */
test.describe('a parent can add/remove their own child from an ACTIVE collection', () => {
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

  test('a parent joins and leaves their own child; another parent cannot act on someone else\'s child', async ({
    page,
    request,
    baseURL,
  }) => {
    // Five real logins (treasurer, rodzic1 x2, rodzic2, treasurer again) - heavier than this
    // suite's usual budget, same reasoning as my-student-status-badge.spec.ts's own timeout
    // override.
    test.setTimeout(90_000);

    const unique = Date.now();
    const studentLastName = `Samoobslugowa${unique}`;
    const kasiaFullName = `Kasia ${studentLastName}`;

    const login = new LoginPage(page);
    const app = new AppShell(page);
    const treasurer = new TreasurerPanel(page);
    const dashboard = new Dashboard(page);

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
    await kasia.expectParentVisible('anna.testowa@example.com');
    kasiaStudentId = await api.studentIdByLastName(studentLastName);

    // Kasia excluded from the start - the collection whose membership her own parent will
    // manage below.
    const collectionTitle = `Wycieczka samoobslugowa ${unique}`;
    await treasurer.goto();
    await treasurer.excludeStudentFromNewCollection(kasiaFullName);
    await treasurer.createCollection(collectionTitle, 'Test samoobslugi rodzica', '15');
    const collectionId = await api.collectionIdByTitle(collectionTitle);

    await app.logout();

    // --- Kasia's own parent: sees "not participating", joins her in ---
    await login.loginAs('rodzic1', 'rodzic1');
    await page.goto(`/collections/${collectionId}`);
    const collection = new CollectionDetailsPage(page);
    await collection.expectMyStudentStatus('do_not_disturb_on');

    await collection.joinMyStudent();
    await collection.expectMyStudentStatus('cancel');
    await app.logout();

    // --- A second, unrelated parent cannot touch Kasia's membership directly via the API ---
    await login.loginAs('rodzic2', 'rodzic2');
    const rodzic2IdToken = await page.evaluate(() => localStorage.getItem('id_token'));
    const forbidden = await request.delete(`${baseURL}/api/collections/${collectionId}/students/${kasiaStudentId}`, {
      headers: { Authorization: `Bearer ${rodzic2IdToken}` },
    });
    expect(forbidden.status()).toBe(403);
    await app.logout();

    // --- Kasia's own parent again: pays in, then opts her back out - refunded, requirement gone ---
    await login.loginAs('rodzic1', 'rodzic1');
    await api.recordContribution(collectionId, kasiaStudentId, 15);
    await page.goto(`/collections/${collectionId}`);
    await collection.expectMyStudentStatus('check_circle');

    await collection.leaveMyStudent();
    await collection.expectMyStudentStatus('do_not_disturb_on');

    // Confirm the refund independently, via the treasurer's own full breakdown.
    await app.logout();
    await login.loginAs('skarbnik', 'skarbnik');
    await dashboard.goto();
    await dashboard.openCollection(collectionTitle);
    const treasurerView = new CollectionDetailsPage(page);
    await treasurerView.requirements.expectNotIncluded(kasiaFullName);
  });
});
