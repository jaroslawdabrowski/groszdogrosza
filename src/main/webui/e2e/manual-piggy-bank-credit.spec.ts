import { test, expect } from '@playwright/test';
import { seedTreasurer } from './seed';
import { Api, GlobalLedgerPage, LoginPage, PiggyBankPage, TreasurerPanel } from './pages';

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
});
