import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test';

/**
 * Page Object Model for the e2e suite - one class per screen (plus a couple of small
 * reusable sub-components), so `main-flow.spec.ts` reads as a story about money and
 * students ("skarb.activityLog.containsEntry('40 zł')") instead of a wall of raw
 * `page.locator(...)` calls. Every method here does exactly the DOM interaction/assertion
 * its name says and nothing else - business-logic decisions (what numbers to expect) stay
 * in the spec file, not here.
 */

/** A `<ul>` of plain-text list items - the same rendering pattern used for a ledger
 *  timeline (student view, global ledger), a collection's contribution list, and a
 *  settlement result list. One class covers all of them. */
export class EntryList {
  constructor(private readonly list: Locator) {}

  async isVisible(): Promise<void> {
    await expect(this.list).toBeVisible();
  }

  async containsEntry(text: string): Promise<void> {
    await expect(this.list).toContainText(text);
  }
}

/** The requirements table on a collection's details page (treasurer view only) - columns
 *  are studentName/requiredAmount/paidAmount/status, see collection-details.html. */
export class RequirementsTable {
  constructor(private readonly table: Locator) {}

  private row(studentFullName: string): Locator {
    return this.table.locator('tr').filter({ hasText: studentFullName });
  }

  async expectRequiredAmount(studentFullName: string, amountZl: string): Promise<void> {
    await expect(this.row(studentFullName).locator('td').nth(1)).toHaveText(`${amountZl} zł`);
  }

  async expectPaidAmount(studentFullName: string, amountZl: string): Promise<void> {
    await expect(this.row(studentFullName).locator('td').nth(2)).toHaveText(`${amountZl} zł`);
  }

  async expectStatus(studentFullName: string, statusText: string): Promise<void> {
    await expect(this.row(studentFullName).locator('td').nth(3)).toContainText(statusText);
  }

  /** A student who was never included in this collection, or who was removed from it (see
   *  CollectionDetailsPage.removeStudent), has no row here at all - not a row showing 0 zł. */
  async expectAbsent(studentFullName: string): Promise<void> {
    await expect(this.table).not.toContainText(studentFullName);
  }

  /** The 5th ("actions") column only exists while the collection is ACTIVE - see
   *  CollectionDetails.visibleRequirementColumns. */
  removeButton(studentFullName: string): Locator {
    return this.row(studentFullName).getByRole('button');
  }
}

export class LoginPage {
  constructor(private readonly page: Page) {}

  /** Logs in against the local dev Keycloak realm (see keycloak-realm.json) - never real
   *  Cognito, this suite only ever runs against `quarkus:dev`. */
  async loginAs(username: string, password: string): Promise<void> {
    await this.page.goto('/login');
    await this.page.getByRole('button', { name: 'Zaloguj się' }).click();
    await this.page.waitForURL(/\/realms\//, { timeout: 15_000 });
    await this.page.locator('#username').fill(username);
    await this.page.locator('#password').fill(password);
    await this.page.locator('#kc-login').click();
    await this.page.waitForURL((url) => !url.pathname.includes('/realms/'), { timeout: 15_000 });
    await this.page.waitForLoadState('networkidle');
  }
}

export class AppShell {
  constructor(private readonly page: Page) {}

  async logout(): Promise<void> {
    await this.page.getByRole('button', { name: 'Log out' }).click();
    await this.page.waitForLoadState('networkidle');
  }

  async openMyPiggyBank(): Promise<void> {
    await this.page.locator('.nav-links a', { hasText: 'Moja skarbonka' }).click();
  }
}

export class Dashboard {
  constructor(private readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/dashboard');
  }

  async expectCollectionVisible(title: string): Promise<void> {
    await expect(this.page.getByText(title)).toBeVisible();
  }

  async openCollection(title: string): Promise<void> {
    // Waits for the resulting client-side route change, not just the click event - a plain
    // `.click()` returns as soon as the click fires, before Angular's routerLink navigation
    // actually lands, which made CollectionDetailsPage.id unreliable when read immediately
    // afterwards instead of after some other auto-waiting assertion.
    await Promise.all([
      this.page.waitForURL(/\/collections\//),
      this.page.getByRole('link', { name: new RegExp(title) }).click(),
    ]);
  }
}

/** One `<li class="student-item">` in the treasurer panel's student list, with its own
 *  nested parent rows - mirrors the nesting in treasurer-panel.html. */
export class StudentItem {
  constructor(private readonly root: Locator) {}

  async expectVisible(): Promise<void> {
    await expect(this.root).toBeVisible();
  }

  async addParent(parent: { firstName: string; lastName: string; email: string; expectedSenderName: string }): Promise<void> {
    await this.root.getByRole('button', { name: 'Dodaj rodzica' }).click();
    const form = this.root.locator('.edit-form');
    await form.getByLabel('Imię').fill(parent.firstName);
    await form.getByLabel('Nazwisko').fill(parent.lastName);
    await form.getByLabel('E-mail').fill(parent.email);
    await form.getByLabel('Nazwa nadawcy na przelewie').fill(parent.expectedSenderName);
    await form.getByRole('button', { name: 'Dodaj rodzica' }).click();
  }

  async expectParentVisible(email: string): Promise<void> {
    await expect(this.root).toContainText(email);
  }
}

export class TreasurerPanel {
  constructor(private readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/treasurer');
  }

  async expectStudentVisible(fullName: string): Promise<void> {
    await expect(this.page.locator('.student-list')).toContainText(fullName);
  }

  /** The "suma w skarbonkach" widget at the top of the panel - sum of every student's
   *  piggy bank balance, treasurer-only (see TreasurerPanel.totalPiggyBankBalance's
   *  javadoc: it's derived from the same GET /api/students the backend already restricts
   *  to a treasurer, on a page already behind treasurerGuard). */
  async expectTotalPiggyBankBalance(amountZl: string): Promise<void> {
    await expect(this.page.locator('.summary-value')).toHaveText(`${amountZl} zł`);
  }

  student(fullName: string): StudentItem {
    return new StudentItem(this.page.locator('.student-item').filter({ hasText: fullName }));
  }

  async addStudent(firstName: string, lastName: string): Promise<StudentItem> {
    await this.page.getByLabel('Imię').fill(firstName);
    await this.page.getByLabel('Nazwisko').fill(lastName);
    await this.page.getByRole('button', { name: 'Dodaj ucznia' }).click();
    const item = this.student(`${firstName} ${lastName}`);
    await item.expectVisible();
    return item;
  }

  async setPaymentInfo(bankAccountNumber: string, blikPhoneNumber: string): Promise<void> {
    await this.page.getByLabel('Numer konta').fill(bankAccountNumber);
    await this.page.getByLabel('BLIK na telefon').fill(blikPhoneNumber);
    await this.page
      .locator('mat-card')
      .filter({ hasText: 'Dane do wpłat' })
      .getByRole('button', { name: 'Zapisz' })
      .click();
    await expect(this.page.getByText('Zapisano.')).toBeVisible();
  }

  /** Every student starts checked on the "who's in this collection" checklist - uncheck one
   *  before calling createCollection to leave them out of it entirely (see
   *  CreateCollectionUseCase's javadoc for why that's different from a 0 zł requirement). */
  async excludeStudentFromNewCollection(studentFullName: string): Promise<void> {
    await this.page.getByRole('checkbox', { name: studentFullName }).uncheck();
  }

  async createCollection(title: string, description: string, baseAmountPerStudentZl: string): Promise<void> {
    await this.page.getByLabel('Tytuł zbiórki').fill(title);
    await this.page.getByLabel('Opis').fill(description);
    await this.page.getByLabel('Kwota bazowa od ucznia (zł)').fill(baseAmountPerStudentZl);
    await this.page.getByRole('button', { name: 'Utwórz zbiórkę' }).click();
    await expect(this.page.getByText('Zbiórka została utworzona.')).toBeVisible();
  }
}

export class CollectionDetailsPage {
  readonly requirements: RequirementsTable;
  readonly contributions: EntryList;
  readonly settlementResult: EntryList;

  constructor(private readonly page: Page) {
    this.requirements = new RequirementsTable(page.locator('table'));
    this.contributions = new EntryList(page.locator('.contribution-list'));
    this.settlementResult = new EntryList(page.locator('.settlement-list'));
  }

  /** The id DynamoDB assigned, read back out of the current URL (`/collections/<id>`). */
  get id(): string {
    return new URL(this.page.url()).pathname.split('/').pop()!;
  }

  async reload(): Promise<void> {
    await this.page.reload();
  }

  async settle(actualCostSpentZl: string): Promise<void> {
    await this.page.locator('input[type="number"]').fill(actualCostSpentZl);
    await this.page.getByRole('button', { name: 'Rozlicz zbiórkę' }).click();
  }

  async expectStatus(status: 'ACTIVE' | 'SETTLED'): Promise<void> {
    await expect(this.page.locator(`.status-chip--${status}`)).toBeVisible();
  }

  /** Accepts the confirm() dialog the button triggers - see
   *  CollectionDetails.removeStudent. Only present while the collection is ACTIVE. */
  async removeStudent(studentFullName: string): Promise<void> {
    this.page.once('dialog', (dialog) => dialog.accept());
    await this.requirements.removeButton(studentFullName).click();
  }
}

export class GlobalLedgerPage {
  readonly activityLog: EntryList;

  constructor(private readonly page: Page) {
    this.activityLog = new EntryList(page.locator('.timeline'));
  }

  async goto(): Promise<void> {
    await this.page.goto('/ledger');
  }
}

/** "Moja skarbonka" - a student's piggy bank + parent contacts + ledger, at
 *  `/students/:id`. Shown to the treasurer for any student, and to a parent for their own
 *  child only (see AuthorizationSupport.requireSelfOrTreasurerForStudent). */
export class PiggyBankPage {
  readonly activityLog: EntryList;

  constructor(private readonly page: Page) {
    this.activityLog = new EntryList(page.locator('.timeline'));
  }

  async goto(studentId: string): Promise<void> {
    await this.page.goto(`/students/${studentId}`);
  }

  async expectStudentName(fullName: string): Promise<void> {
    await expect(this.page.locator('h1')).toContainText(fullName);
  }

  async expectBalance(amountZl: string): Promise<void> {
    await expect(this.page.locator('.piggy-balance')).toHaveText(`${amountZl} zł`);
  }
}

/** Thin wrapper around the raw REST API for the handful of things this suite needs that
 *  have no UI yet (recording a "payment" - see main-flow.spec.ts's class comment for why)
 *  or are just faster/more reliable done directly (resolving a UI-created student's id,
 *  cleanup). Not a page object - there's no page - but kept in this file since it's the
 *  same "give the spec a readable noun to call methods on" idea. */
export class Api {
  constructor(
    private readonly request: APIRequestContext,
    private readonly baseURL: string,
    private readonly treasurerIdToken: string,
  ) {}

  private get headers(): Record<string, string> {
    return { Authorization: `Bearer ${this.treasurerIdToken}` };
  }

  /** The test only knows a lastName, not the id DynamoDB assigned when the UI created the
   *  student - resolve it the same way the app itself would, via the real API. */
  async studentIdByLastName(lastName: string): Promise<string> {
    const resp = await this.request.get(`${this.baseURL}/api/students`, { headers: this.headers });
    const students = (await resp.json()) as Array<{ id: string; lastName: string }>;
    const match = students.find((s) => s.lastName === lastName);
    if (!match) {
      throw new Error(`No student with lastName ${lastName} found via GET /api/students`);
    }
    return match.id;
  }

  /** Stands in for "a payment arrived" - see main-flow.spec.ts's class comment for why this
   *  isn't a UI click. */
  async recordContribution(collectionId: string, studentId: string, amountZl: number): Promise<void> {
    const resp = await this.request.post(`${this.baseURL}/api/collections/${collectionId}/contributions`, {
      headers: this.headers,
      data: { studentId, amount: amountZl },
    });
    if (resp.status() !== 200) {
      throw new Error(`Recording a contribution of ${amountZl} zł for student ${studentId} failed: ` +
        `${resp.status()} ${await resp.text()}`);
    }
  }

  /** Treasurer-only manual top-up (`CreditStudentPiggyBankManuallyUseCase`) - used to give a
   *  student an existing piggy bank balance *before* a collection is created, so its
   *  requirement is computed against a non-zero starting balance. */
  async creditPiggyBank(studentId: string, amountZl: number): Promise<void> {
    const resp = await this.request.post(`${this.baseURL}/api/students/${studentId}/piggy-bank/credit`, {
      headers: this.headers,
      data: { amount: amountZl },
    });
    if (resp.status() !== 200) {
      throw new Error(`Crediting ${amountZl} zł to student ${studentId}'s piggy bank failed: ` +
        `${resp.status()} ${await resp.text()}`);
    }
  }

  async deleteStudent(studentId: string): Promise<void> {
    await this.request.delete(`${this.baseURL}/api/students/${studentId}`, { headers: this.headers }).catch(() => {});
  }
}
