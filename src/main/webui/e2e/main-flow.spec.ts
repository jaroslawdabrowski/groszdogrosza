import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { seedTreasurer } from './seed';

/**
 * The main money flow, end to end, against a real running app (see e2e/README.md for how to
 * run it) - not mocked, not a unit test. One long sequential test rather than many small
 * ones: each step depends on state the previous step created (a collection to pay into, a
 * parent account to log in as), so splitting it up would just mean re-doing setup per test
 * for no real isolation benefit.
 *
 * Money is "paid in" via the treasurer directly recording a contribution
 * (`POST /api/collections/{id}/contributions`) rather than through a UI button, because
 * there isn't one yet (see CLAUDE.md's "No UI to manually record a contribution" TODO) - and
 * because a real parent payment normally arrives via the automatic bank-statement pipeline
 * (`BankStatementProcessingService`), not a UI action a parent takes themselves. That
 * piggy-bank-first-then-sweep pipeline is covered separately by
 * `PaymentMatchingPolicyTest`/`ContributionAllocationPolicyTest` and was manually verified
 * against a mocked mailbox (see CLAUDE.md, "Verified end-to-end locally") - this test's job
 * is the rest of the flow: creating the collection, the money actually landing against the
 * right student, the settlement math, and both the treasurer's and a parent's view of it.
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
  let treasurerIdToken: string | undefined;
  let treasurerKidStudentId: string | undefined;
  let kasiaStudentId: string | undefined;

  test.afterEach(async ({ request, baseURL }) => {
    if (!treasurerIdToken) {
      return;
    }
    const headers = { Authorization: `Bearer ${treasurerIdToken}` };
    if (kasiaStudentId) {
      await request.delete(`${baseURL}/api/students/${kasiaStudentId}`, { headers }).catch(() => {});
    }
    if (treasurerKidStudentId) {
      await request.delete(`${baseURL}/api/students/${treasurerKidStudentId}`, { headers }).catch(() => {});
    }
  });

  test('treasurer creates a collection, a payment is recorded, it settles, both logins see the right numbers', async ({
    page,
    request,
    baseURL,
  }) => {
    const unique = Date.now();
    // Must be the exact email keycloak-realm.json's "skarbnik" dev user logs in as -
    // AuthorizationSupport resolves the caller's own Parent record by matching this email
    // against the OIDC token's email claim, not by any stored subject id.
    const seeded = seedTreasurer('skarbnik@example.com', 'Jarek', 'Skarbnik', 'Jasio', 'Skarbnik');
    treasurerKidStudentId = seeded.studentId;

    await loginAsKeycloakUser(page, 'skarbnik', 'skarbnik');
    treasurerIdToken = await page.evaluate(() => sessionStorage.getItem('id_token'));

    await page.goto('/treasurer');
    await expect(page.locator('.student-list')).toContainText('Jasio Skarbnik');

    // --- Add a second student with a parent, via the real UI ---
    const studentLastName = `Testowa${unique}`;
    await page.getByLabel('Imię').fill('Kasia');
    await page.getByLabel('Nazwisko').fill(studentLastName);
    await page.getByRole('button', { name: 'Dodaj ucznia' }).click();
    const studentItem = page.locator('.student-item').filter({ hasText: `Kasia ${studentLastName}` });
    await expect(studentItem).toBeVisible();

    await studentItem.getByRole('button', { name: 'Dodaj rodzica' }).click();
    const addParentForm = studentItem.locator('.edit-form');
    await addParentForm.getByLabel('Imię').fill('Anna');
    await addParentForm.getByLabel('Nazwisko').fill(studentLastName);
    // Must match keycloak-realm.json's "rodzic1" dev user's real email exactly - same
    // email-claim-matching rule as the treasurer seeding above.
    await addParentForm.getByLabel('E-mail').fill('anna.testowa@example.com');
    await addParentForm.getByLabel('Nazwa nadawcy na przelewie').fill(`Anna ${studentLastName}`);
    await addParentForm.getByRole('button', { name: 'Dodaj rodzica' }).click();
    await expect(studentItem).toContainText('anna.testowa@example.com');

    kasiaStudentId = await studentIdByLastName(request, baseURL!, treasurerIdToken!, studentLastName);

    // --- Payment info (shown on the public, unauthenticated page) ---
    await page.getByLabel('Numer konta').fill('11 2222 3333 4444 5555 6666 7777');
    await page.getByLabel('BLIK na telefon').fill('600 700 800');
    await page.locator('mat-card').filter({ hasText: 'Dane do wpłat' }).getByRole('button', { name: 'Zapisz' }).click();
    await expect(page.getByText('Zapisano.')).toBeVisible();

    // --- Create the collection - one requirement per student, including the one just added ---
    const collectionTitle = `Prezent testowy ${unique}`;
    await page.getByLabel('Tytuł zbiórki').fill(collectionTitle);
    await page.getByLabel('Opis').fill('Zbiórka utworzona przez test end-to-end');
    await page.getByLabel('Kwota bazowa od ucznia (zł)').fill('50');
    await page.getByRole('button', { name: 'Utwórz zbiórkę' }).click();
    await expect(page.getByText('Zbiórka została utworzona.')).toBeVisible();

    await page.goto('/dashboard');
    await page.getByRole('link', { name: new RegExp(collectionTitle) }).click();
    await expect(page.locator('table')).toContainText(studentLastName);
    await expect(page.locator('table')).toContainText('Do zapłaty');

    const collectionId = new URL(page.url()).pathname.split('/').pop()!;

    // --- "A parent pays" - recorded the way a real incoming bank transfer would book it,
    // see the class-level comment above for why this isn't a UI click. ---
    const contributionResp = await request.post(`${baseURL}/api/collections/${collectionId}/contributions`, {
      headers: { Authorization: `Bearer ${treasurerIdToken}` },
      data: { studentId: kasiaStudentId, amount: 50 },
    });
    expect(contributionResp.status()).toBe(200);

    await page.reload();
    await expect(page.locator('table')).toContainText('Zapłacone');
    await expect(page.locator('.contribution-list')).toContainText('50 zł');

    // --- Settle: contributed 50, actual cost 40 -> 10 zł surplus credited back ---
    await page.locator('input[type="number"]').fill('40');
    await page.getByRole('button', { name: 'Rozlicz zbiórkę' }).click();
    await expect(page.locator('.settlement-list')).toContainText('+10 zł');
    await expect(page.locator('.status-chip--SETTLED')).toBeVisible();

    // --- Treasurer-only global ledger shows the same student's events ---
    await page.goto('/ledger');
    await expect(page.locator('mat-card')).toContainText(`Kasia ${studentLastName}`);
    await expect(page.locator('mat-card')).toContainText('50 zł');

    await logout(page);

    // --- The parent logs in and sees their own child's collection and piggy bank ---
    await loginAsKeycloakUser(page, 'rodzic1', 'rodzic1');

    await page.goto('/dashboard');
    await expect(page.getByText(collectionTitle)).toBeVisible();

    await page.locator('.nav-links a', { hasText: 'Moja skarbonka' }).click();
    await expect(page.locator('h1')).toContainText(`Kasia ${studentLastName}`);
    await expect(page.locator('.piggy-balance')).toHaveText('10 zł');
    await expect(page.locator('.timeline')).toContainText('Wpłata');
  });
});

async function loginAsKeycloakUser(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/login');
  await page.getByRole('button', { name: 'Zaloguj się' }).click();
  await page.waitForURL(/\/realms\//, { timeout: 15_000 });
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
  await page.waitForURL((url) => !url.pathname.includes('/realms/'), { timeout: 15_000 });
  await page.waitForLoadState('networkidle');
}

async function logout(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Log out' }).click();
  await page.waitForLoadState('networkidle');
}

/** The test only knows a lastName, not the id DynamoDB assigned when the UI created the
 *  student - resolve it the same way the app itself would, via the real API. */
async function studentIdByLastName(
  request: APIRequestContext,
  baseURL: string,
  idToken: string,
  lastName: string,
): Promise<string> {
  const resp = await request.get(`${baseURL}/api/students`, { headers: { Authorization: `Bearer ${idToken}` } });
  const students = (await resp.json()) as Array<{ id: string; lastName: string }>;
  const match = students.find((s) => s.lastName === lastName);
  if (!match) {
    throw new Error(`No student with lastName ${lastName} found via GET /api/students`);
  }
  return match.id;
}
