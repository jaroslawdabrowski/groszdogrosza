import { test, expect } from '@playwright/test';
import { seedTreasurer } from './seed';
import { Api, LoginPage, TreasurerPanel } from './pages';

/**
 * The "suma w skarbonkach" widget at the top of the treasurer panel - the treasurer's own
 * worked request: "so I know how much of parents' money I'm holding". Sums every student's
 * piggy bank balance. Treasurer-only - not a separate access check of its own, it inherits
 * that from the same two things that already gate the rest of this page: the backend only
 * ever returns the student list (with balances) to a treasurer
 * (`AuthorizationSupport.requireTreasurer` in `StudentResource.list`), and the page itself
 * is behind `treasurerGuard` client-side. This suite proves both halves: the widget shows
 * the right sum for a treasurer, and a regular parent can't reach the page (widget
 * included) at all.
 *
 * Also covers the per-student piggy bank balance chip shown under each student's own name
 * (`.student-balance` in treasurer-panel.html) - a follow-up request from the treasurer to
 * see one child's current balance right in the roster, not just the class-wide total.
 */
test.describe('treasurer panel: total piggy bank balance widget', () => {
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

  test('sums every student\'s piggy bank balance, and only a treasurer can see it', async ({ page, request, baseURL }) => {
    const unique = Date.now();
    const studentLastName = `Widgetowa${unique}`;

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
    // Nobody has any money saved yet - 0 zł, not blank/missing.
    await treasurer.expectTotalPiggyBankBalance('0');

    const zosia = await treasurer.addStudent('Zosia', studentLastName);
    await zosia.addParent({
      firstName: 'Piotr',
      lastName: studentLastName,
      email: `piotr.${unique}@example.com`,
      expectedSenderName: `Piotr ${studentLastName}`,
    });
    zosiaStudentId = await api.studentIdByLastName(studentLastName);

    // Two students, two different balances - the widget must be the SUM, not just one of them.
    await api.creditPiggyBank(jasioStudentId, 40);
    await api.creditPiggyBank(zosiaStudentId, 15);

    await treasurer.goto();
    await treasurer.expectTotalPiggyBankBalance('55');
    // Each student's OWN row also shows their own balance, not the total - the treasurer's
    // own worked request ("chce widzieć stan aktualny skarbonki tego dziecka").
    await treasurer.student('Jasio Skarbnik').expectPiggyBankBalance('40');
    await treasurer.student(`Zosia ${studentLastName}`).expectPiggyBankBalance('15');

    // --- A regular parent can't reach the treasurer panel - the widget included - at all ---
    await page.getByRole('button', { name: 'Log out' }).click();
    await page.waitForLoadState('networkidle');
    await login.loginAs('rodzic1', 'rodzic1');
    await page.goto('/treasurer');
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.locator('.summary-value')).toHaveCount(0);
  });
});
