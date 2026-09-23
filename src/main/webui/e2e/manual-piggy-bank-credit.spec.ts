import { test, expect } from '@playwright/test';
import { seedTreasurer } from './seed';
import { Api, CollectionDetailsPage, Dashboard, GlobalLedgerPage, LoginPage, PiggyBankPage, TreasurerPanel } from './pages';

/**
 * "Doładuj skarbonkę" on a student's row in the treasurer panel - the treasurer's own
 * request: someone handed them cash by hand (not a bank transfer), and they want to add it
 * to that student's piggy bank without waiting for the automatic bank-statement pipeline.
 * Deliberately credit-only, not a "set the balance to X" control (see
 * TreasurerPanel.creditPiggyBank's javadoc) - the backend itself
 * (`StudentService.creditPiggyBank`) rejects a negative amount, so there is no path to this
 * silently erasing money that's already there. Every top-up records a PIGGY_BANK_CREDITED
 * ledger entry (`StudentService.creditPiggyBankManually`), same as every other
 * money-crediting path in this app - this suite checks it shows up both on the student's
 * own ledger and the treasurer's global one.
 */
test.describe('treasurer panel: manual piggy bank top-up', () => {
  let api: Api | undefined;
  let jasioStudentId: string | undefined;
  let studentId: string | undefined;

  test.afterEach(async () => {
    if (!api) {
      return;
    }
    if (studentId) {
      await api.deleteStudent(studentId);
    }
    if (jasioStudentId) {
      await api.deleteStudent(jasioStudentId);
    }
  });

  test('crediting a student\'s piggy bank by hand adds to the balance and is logged', async ({ page, request, baseURL }) => {
    const unique = Date.now();
    const studentLastName = `Gotowkowa${unique}`;

    const login = new LoginPage(page);
    const treasurer = new TreasurerPanel(page);

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
    studentId = await api.studentIdByLastName(studentLastName);
    await zosia.expectPiggyBankBalance('0');

    // First top-up - starts from zero.
    await zosia.creditPiggyBank('30');
    await treasurer.goto();
    await treasurer.student(`Zosia ${studentLastName}`).expectPiggyBankBalance('30');

    // A second top-up ADDS to the existing balance, never replaces it - the whole point of
    // "credit-only, not set-the-value".
    await treasurer.student(`Zosia ${studentLastName}`).creditPiggyBank('20');
    await treasurer.goto();
    await treasurer.student(`Zosia ${studentLastName}`).expectPiggyBankBalance('50');

    // --- Both top-ups are logged, on the student's own ledger and the treasurer's global one ---
    const piggyBank = new PiggyBankPage(page);
    await piggyBank.goto(studentId);
    await piggyBank.expectBalance('50');
    await piggyBank.activityLog.containsEntry('30 zł zasiliła skarbonkę');
    await piggyBank.activityLog.containsEntry('20 zł zasiliła skarbonkę');

    const globalLedger = new GlobalLedgerPage(page);
    await globalLedger.goto();
    await globalLedger.activityLog.containsEntry(`Zosia ${studentLastName}`);
    await globalLedger.activityLog.containsEntry('30 zł zasiliła skarbonkę');
    await globalLedger.activityLog.containsEntry('20 zł zasiliła skarbonkę');
  });

  /**
   * Regression test for a real production report (2026-09-23): the treasurer credited 100 zł
   * cash to a student who already owed money on an ACTIVE collection, and the collection's
   * requirement stayed untouched - the 100 zł just sat in the piggy bank instead of being
   * swept in immediately, the way the exact same amount arriving via the automatic
   * bank-statement pipeline already is. Root cause: `StudentService.creditPiggyBankManually`
   * only credited the balance and logged it, with no equivalent of
   * `BankStatementProcessingService.bookMatchedTransaction`'s sweep step - fixed via the new
   * `SweepPiggyBankIntoActiveCollectionsUseCase`, called right after every manual credit.
   */
  test('crediting cash for a student who already owes money on an ACTIVE collection sweeps it in immediately', async ({
    page,
    request,
    baseURL,
  }) => {
    const unique = Date.now();
    const studentLastName = `Stolarzowa${unique}`;
    const studentFullName = `Zosia ${studentLastName}`;

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
    studentId = await api.studentIdByLastName(studentLastName);
    await zosia.expectPiggyBankBalance('0');

    // An ACTIVE collection asking 40 zł, created BEFORE any cash arrives - Zosia owes the
    // full amount, nothing pre-covers it.
    const collectionTitle = `Wpłata na klasę ${unique}`;
    await treasurer.goto();
    await treasurer.createCollection(collectionTitle, 'Test zamiatania gotówki', '40');
    await dashboard.goto();
    await dashboard.openCollection(collectionTitle);
    const collection = new CollectionDetailsPage(page);
    await collection.requirements.expectRequiredAmount(studentFullName, '40');
    await collection.requirements.expectPaidAmount(studentFullName, '0');
    await collection.requirements.expectStatus(studentFullName, 'Do zapłaty');

    // --- The cash top-up: 100 zł, more than enough to cover the 40 zł owed ---
    await treasurer.goto();
    await treasurer.student(studentFullName).creditPiggyBank('100');

    // The balance reflects the sweep, not the raw 100 zł just credited - 60 zł left over
    // after 40 zł was swept into the collection.
    await treasurer.goto();
    await treasurer.student(studentFullName).expectPiggyBankBalance('60');

    await dashboard.goto();
    await dashboard.openCollection(collectionTitle);
    await collection.requirements.expectPaidAmount(studentFullName, '40');
    await collection.requirements.expectStatus(studentFullName, 'Zapłacone');

    await piggyBank.goto(studentId);
    await piggyBank.expectBalance('60');
    await piggyBank.activityLog.containsEntry('100 zł zasiliła skarbonkę');
    await piggyBank.activityLog.containsEntry('40 zł');
    await piggyBank.activityLog.containsEntry('zbiórkę');
  });
});
