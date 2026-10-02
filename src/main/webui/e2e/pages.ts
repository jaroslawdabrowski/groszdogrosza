import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test';

/**
 * Page Object Model for the e2e suite - one class per screen (plus a couple of small
 * reusable sub-components), so `main-flow.spec.ts` reads as a story about money and
 * students ("skarb.activityLog.containsEntry('40 zł')") instead of a wall of raw
 * `page.locator(...)` calls. Every method here does exactly the DOM interaction/assertion
 * its name says and nothing else - business-logic decisions (what numbers to expect) stay
 * in the spec file, not here.
 */

/** A list of plain-text rows - the same rendering pattern used for a ledger (student view,
 *  global ledger), a collection's contribution list, and a settlement result. One class
 *  covers all of them. */
export class EntryList {
  constructor(private readonly list: Locator) {}

  async isVisible(): Promise<void> {
    await expect(this.list.first()).toBeVisible();
  }

  async containsEntry(text: string): Promise<void> {
    await expect(this.list.filter({ hasText: text }).first()).toBeVisible();
  }

  /** One row containing every one of `texts` (e.g. a student's name and their amount). */
  async containsEntryWith(...texts: string[]): Promise<void> {
    let rows = this.list;
    for (const text of texts) {
      rows = rows.filter({ hasText: text });
    }
    await expect(rows.first()).toBeVisible();
  }
}

/** The student list on a collection's details page (treasurer view only) - one
 *  `data-testid="student-row"` per student, see collection-details.html. Rows carry their
 *  student's full name in `data-name` and requirement status in `data-status`. */
export class RequirementsTable {
  constructor(private readonly page: Page) {}

  private row(studentFullName: string): Locator {
    return this.page.locator(`[data-testid="student-row"][data-name="${studentFullName}"]`);
  }

  async expectRequiredAmount(studentFullName: string, amountZl: string): Promise<void> {
    await expect(this.row(studentFullName).getByTestId('required-amount')).toHaveText(amountZl);
  }

  async expectPaidAmount(studentFullName: string, amountZl: string): Promise<void> {
    await expect(this.row(studentFullName).getByTestId('paid-amount')).toHaveText(amountZl);
  }

  async expectStatus(studentFullName: string, statusText: string): Promise<void> {
    await expect(this.row(studentFullName).getByTestId('student-status')).toContainText(statusText);
  }

  /** A student truly absent from the list altogether - not even shown greyed out. Since
   *  CollectionDetails.rosterRows merges in the full class roster (ACTIVE and SETTLED alike -
   *  see expectNotIncluded below), this only applies to someone not in the class at all. */
  async expectAbsent(studentFullName: string): Promise<void> {
    await expect(this.row(studentFullName)).toHaveCount(0);
  }

  /** A student NOT part of this collection (never added, or removed earlier) still shows up
   *  as a row - greyed out, with a dash instead of amounts and an "add back" action instead
   *  of "remove" while ACTIVE (see CollectionDetails.rosterRows). */
  async expectNotIncluded(studentFullName: string): Promise<void> {
    const row = this.row(studentFullName);
    await expect(row).toHaveAttribute('data-status', 'NOT_INCLUDED');
    await expect(row.getByTestId('paid-amount')).toHaveText('–');
    await expect(row.getByTestId('student-status')).toContainText('Nie w zbiórce');
  }

  /** Only rendered while the collection is ACTIVE. */
  removeButton(studentFullName: string): Locator {
    return this.row(studentFullName).getByTestId('remove-student');
  }

  addButton(studentFullName: string): Locator {
    return this.row(studentFullName).getByTestId('add-student');
  }
}

/** The "Rozliczenie zbiórki" panel's at-a-glance numbers (paid-count, collected, expected,
 *  removed) plus the actual-cost input it auto-fills - see CollectionDetails.paidStudentsCount /
 *  totalStudentsCount / totalExpected and reload()'s actualCostSpent pre-fill. */
export class SettleSummary {
  constructor(private readonly root: Locator) {}

  private stat(index: number): Locator {
    return this.root.getByTestId('settle-stat').nth(index);
  }

  async expectStudentsPaid(paid: number, total: number): Promise<void> {
    await expect(this.stat(0)).toContainText(`${paid} / ${total}`);
  }

  async expectTotalCollected(amountZl: string): Promise<void> {
    await expect(this.stat(1)).toContainText(`${amountZl} zł`);
  }

  async expectTotalExpected(amountZl: string): Promise<void> {
    await expect(this.stat(2)).toContainText(`${amountZl} zł`);
  }

  /** Only rendered at all once at least one student has been removed from this collection -
   *  see CollectionDetails.removedStudentsCount's javadoc for why 0 is deliberately hidden
   *  rather than shown as "0". */
  async expectRemovedStudentsCount(count: number): Promise<void> {
    await expect(this.stat(3)).toContainText(String(count));
  }

  async expectRemovedStudentsCountAbsent(): Promise<void> {
    await expect(this.root.getByTestId('settle-stat')).toHaveCount(3);
  }

  /** The cost input starts pre-filled with what's actually been collected so far (see
   *  CollectionDetails.reload), not left at 0 for the treasurer to fill in by hand. */
  async expectActualCostPrefilled(amountZl: string): Promise<void> {
    await expect(this.root.getByLabel('Rzeczywisty koszt (zł)')).toHaveValue(amountZl);
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
    await this.page.getByRole('button', { name: 'Wyloguj się' }).click();
    await this.page.waitForLoadState('networkidle');
  }

  /** The sidebar link (the suite runs at desktop width, where the sidebar replaces the
   *  phone tab bar - see app.html). */
  async openMyPiggyBank(): Promise<void> {
    await this.page.getByRole('navigation').getByRole('link', { name: 'Moja skarbonka' }).click();
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

  /** "Does my child take part / have they paid" - the pill on a collection's row carries
   *  the viewer's own child's status (PENDING / PAID / OVERPAID / NOT_INCLUDED) in
   *  `data-status`. */
  async expectMyStudentStatus(title: string, status: string): Promise<void> {
    const row = this.page.locator(`[data-testid="collection-row"][data-title="${title}"]`);
    await expect(row.getByTestId('my-status')).toHaveAttribute('data-status', status);
  }

  /** No logged-in parent resolvable to a child - the pill then shows the collection's own
   *  status and carries no `data-status`. */
  async expectMyStudentStatusAbsent(title: string): Promise<void> {
    const row = this.page.locator(`[data-testid="collection-row"][data-title="${title}"]`);
    await expect(row.getByTestId('my-status')).not.toHaveAttribute('data-status', /.+/);
  }
}

/** The unauthenticated landing page ("/") - shows every active collection's aggregate
 *  progress, with no login required. See PublicOverview's own javadoc for why a logged-in
 *  visitor's card here is clickable through to the same /collections/:id detail view
 *  Dashboard's own cards use, while an anonymous visitor's isn't (kept off a route behind
 *  authGuard entirely). */
export class PublicOverviewPage {
  constructor(private readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/');
  }

  async openCollection(title: string): Promise<void> {
    await Promise.all([
      this.page.waitForURL(/\/collections\//),
      this.page.getByRole('link', { name: new RegExp(title) }).click(),
    ]);
  }

  /** For an anonymous visitor, the card must render as plain (non-navigating) content, not
   *  a link into a route that would just bounce them to a login wall. */
  async expectCollectionNotClickable(title: string): Promise<void> {
    await expect(this.page.getByRole('link', { name: new RegExp(title) })).toHaveCount(0);
    await expect(this.page.getByText(title)).toBeVisible();
  }

  /** The "your child owes / has paid / isn't taking part" block on a collection's sheet.
   *  The public endpoint still resolves it for a logged-in visitor (authInterceptor attaches
   *  the bearer token even to this unauthenticated call - see backend
   *  PublicOverviewResource's javadoc). */
  async expectMyStudentStatus(title: string, status: string): Promise<void> {
    const sheet = this.page.locator(`[data-testid="collection-sheet"][data-title="${title}"]`);
    await expect(sheet.getByTestId('my-status')).toHaveAttribute('data-status', status);
  }

  /** For an anonymous visitor (or a logged-in one with no linked child), no block at all. */
  async expectMyStudentStatusAbsent(title: string): Promise<void> {
    const sheet = this.page.locator(`[data-testid="collection-sheet"][data-title="${title}"]`);
    await expect(sheet).toBeVisible();
    await expect(sheet.getByTestId('my-status')).toHaveCount(0);
  }
}

/** One student in the class panel's list, with its own nested parent rows - mirrors the
 *  nesting in treasurer-panel.html. Its actions (add parent, credit cash, edit, delete)
 *  unfold from the row's "more" button. */
export class StudentItem {
  constructor(private readonly root: Locator) {}

  async expectVisible(): Promise<void> {
    await expect(this.root).toBeVisible();
  }

  private async openActions(): Promise<void> {
    const toggle = this.root.getByTestId('student-actions');
    if ((await toggle.getAttribute('aria-expanded')) !== 'true') {
      await toggle.click();
    }
  }

  async addParent(parent: { firstName: string; lastName: string; email: string; expectedSenderName: string }): Promise<void> {
    await this.openActions();
    await this.root.getByRole('button', { name: 'Dodaj rodzica' }).click();
    const form = this.root.getByTestId('add-parent-form');
    await form.getByLabel('Imię').fill(parent.firstName);
    await form.getByLabel('Nazwisko').fill(parent.lastName);
    await form.getByLabel('E-mail').fill(parent.email);
    await form.getByLabel('Nazwa nadawcy na przelewie').fill(parent.expectedSenderName);
    await form.getByRole('button', { name: 'Dodaj rodzica' }).click();
    await expect(form).toHaveCount(0);
  }

  async expectParentVisible(email: string): Promise<void> {
    await expect(this.root).toContainText(email);
  }

  /** The piggy bank balance on this student's row. */
  async expectPiggyBankBalance(amountZl: string): Promise<void> {
    await expect(this.root.getByTestId('student-balance')).toHaveText(`${amountZl} zł`);
  }

  /** Manual cash top-up ("ktoś dał mi gotówkę") - treasurer-only, credit-only (see
   *  TreasurerPanel.creditPiggyBank's javadoc: adds to the balance, never replaces it). */
  async creditPiggyBank(amountZl: string): Promise<void> {
    await this.openActions();
    await this.root.getByRole('button', { name: 'Doładuj skarbonkę gotówką' }).click();
    const form = this.root.getByTestId('credit-form');
    await form.getByLabel('Kwota (zł)').fill(amountZl);
    await form.getByRole('button', { name: 'Doładuj skarbonkę', exact: true }).click();
  }
}

/** "Klasa" - the treasurer's panel, three tabs: Uczniowie / Nowa zbiórka / Dane do wpłat. */
export class TreasurerPanel {
  constructor(private readonly page: Page) {}

  async goto(): Promise<void> {
    await this.page.goto('/treasurer');
  }

  private async openTab(name: string): Promise<void> {
    await this.page.getByRole('tab', { name }).click();
  }

  async expectStudentVisible(fullName: string): Promise<void> {
    await this.openTab('Uczniowie');
    await expect(this.page.locator('.student-list')).toContainText(fullName);
  }

  /** The "suma w skarbonkach" figure at the top of the panel - sum of every student's
   *  piggy bank balance, treasurer-only (see TreasurerPanel.totalPiggyBankBalance's
   *  javadoc). */
  async expectTotalPiggyBankBalance(amountZl: string): Promise<void> {
    await expect(this.page.getByTestId('total-piggy-balance')).toHaveText(`${amountZl}zł`);
  }

  student(fullName: string): StudentItem {
    return new StudentItem(this.page.locator(`[data-testid="student-item"][data-name="${fullName}"]`));
  }

  async addStudent(firstName: string, lastName: string): Promise<StudentItem> {
    await this.openTab('Uczniowie');
    const form = this.page.getByTestId('add-student-form');
    await form.getByLabel('Imię').fill(firstName);
    await form.getByLabel('Nazwisko').fill(lastName);
    await form.getByRole('button', { name: 'Dodaj ucznia' }).click();
    const item = this.student(`${firstName} ${lastName}`);
    await item.expectVisible();
    return item;
  }

  async setPaymentInfo(bankAccountNumber: string, blikPhoneNumber: string): Promise<void> {
    await this.openTab('Dane do wpłat');
    const form = this.page.getByTestId('payment-info-form');
    await form.getByLabel('Numer konta').fill(bankAccountNumber);
    await form.getByLabel('BLIK na telefon').fill(blikPhoneNumber);
    await form.getByRole('button', { name: 'Zapisz' }).click();
    await expect(form.getByText('Zapisano.')).toBeVisible();
  }

  /** Every student starts checked on the "who's in this collection" checklist - uncheck one
   *  before calling createCollection to leave them out of it entirely (see
   *  CreateCollectionUseCase's javadoc for why that's different from a 0 zł requirement). */
  async excludeStudentFromNewCollection(studentFullName: string): Promise<void> {
    await this.openTab('Nowa zbiórka');
    await this.page.getByRole('checkbox', { name: studentFullName }).uncheck();
  }

  async createCollection(title: string, description: string, baseAmountPerStudentZl: string): Promise<void> {
    await this.openTab('Nowa zbiórka');
    const form = this.page.getByTestId('new-collection-form');
    await form.getByLabel('Tytuł zbiórki').fill(title);
    await form.getByLabel('Opis').fill(description);
    await form.getByLabel('Kwota bazowa od ucznia (zł)').fill(baseAmountPerStudentZl);
    await form.getByRole('button', { name: 'Utwórz zbiórkę' }).click();
    await expect(form.getByText('Zbiórka została utworzona.')).toBeVisible();
  }
}

export class CollectionDetailsPage {
  readonly requirements: RequirementsTable;
  readonly contributions: EntryList;
  readonly settlementResult: EntryList;
  readonly settleSummary: SettleSummary;

  constructor(private readonly page: Page) {
    this.requirements = new RequirementsTable(page);
    this.contributions = new EntryList(page.getByTestId('contribution-row'));
    this.settlementResult = new EntryList(page.getByTestId('settlement-row'));
    this.settleSummary = new SettleSummary(page.getByTestId('settle-panel'));
  }

  /** The id DynamoDB assigned, read back out of the current URL (`/collections/<id>`). */
  get id(): string {
    return new URL(this.page.url()).pathname.split('/').pop()!;
  }

  async reload(): Promise<void> {
    await this.page.reload();
  }

  async settle(actualCostSpentZl: string): Promise<void> {
    await this.page.getByLabel('Rzeczywisty koszt (zł)').fill(actualCostSpentZl);
    // Two steps - a preview of who gets what back, then the real settle (CollectionDetails).
    await this.page.getByRole('button', { name: 'Pokaż, jak się rozliczy' }).click();
    await this.page.getByRole('button', { name: 'Zatwierdź rozliczenie' }).click();
  }

  async expectStatus(status: 'ACTIVE' | 'SETTLED'): Promise<void> {
    await expect(this.page.getByTestId('collection-status')).toHaveText(status === 'ACTIVE' ? 'Aktywna' : 'Rozliczona');
  }

  /** "Does my child take part / have they paid" on this page's hero sheet - same
   *  `data-status` values as Dashboard/PublicOverviewPage's own expectMyStudentStatus. */
  async expectMyStudentStatus(status: string): Promise<void> {
    await expect(this.page.getByTestId('my-status')).toHaveAttribute('data-status', status);
  }

  /** Self-service opt-in for a regular parent's OWN child, only visible while ACTIVE and
   *  only when myStudentStatus is 'NOT_INCLUDED' - see CollectionDetails.addMyStudent /
   *  backend AuthorizationSupport.requireSelfOrTreasurerForStudent. */
  async joinMyStudent(): Promise<void> {
    await this.page.getByRole('button', { name: 'Zapisz swoje dziecko do tej zbiórki' }).click();
  }

  /** Self-service opt-out, with the same confirm() dialog pattern as the treasurer's own
   *  removeStudent - see CollectionDetails.removeMyStudent. */
  async leaveMyStudent(): Promise<void> {
    this.page.once('dialog', (dialog) => dialog.accept());
    await this.page.getByRole('button', { name: 'Wypisz swoje dziecko z tej zbiórki' }).click();
  }

  /** Treasurer-only - see CollectionDetails.print's javadoc: it's only rendered inside the
   *  `isCollectionDetails` branch the backend restricts to a treasurer in the first place. */
  async expectPrintButtonVisible(): Promise<void> {
    await expect(this.page.getByRole('button', { name: 'Drukuj raport' })).toBeVisible();
  }

  async expectPrintButtonAbsent(): Promise<void> {
    await expect(this.page.getByRole('button', { name: 'Drukuj raport' })).toHaveCount(0);
  }

  /** Switches this page's CSS media emulation to "print" (Playwright talks to the real
   *  browser engine, so this exercises the actual `@media print` rules - not a guess at what
   *  they'd do). Caller is responsible for switching back with `exitPrintPreview` if the test
   *  keeps using the page afterwards. */
  async enterPrintPreview(): Promise<void> {
    await this.page.emulateMedia({ media: 'print' });
  }

  async exitPrintPreview(): Promise<void> {
    await this.page.emulateMedia({ media: 'screen' });
  }

  async expectPrintOnlyContentVisible(): Promise<void> {
    await expect(this.page.locator('.print-header')).toBeVisible();
    await expect(this.page.locator('.total-collected')).toBeVisible();
  }

  /** Everything that makes sense to click but not to read on paper - the toolbar, the
   *  back-link, the settle form, and the interactive student list (paper gets a plain table
   *  instead) - is hidden once print CSS applies (see the `.no-print` rules in
   *  styles.scss/collection-details.scss). */
  async expectInteractiveChromeHiddenForPrint(): Promise<void> {
    await expect(this.page.getByRole('navigation').first()).toBeHidden();
    await expect(this.page.locator('.back-link')).toBeHidden();
    await expect(this.page.getByTestId('student-list')).toBeHidden();
    await expect(this.page.getByTestId('settle-panel')).toBeHidden();
    await expect(this.page.locator('.print-table')).toBeVisible();
  }

  /** Accepts the confirm() dialog the button triggers - see
   *  CollectionDetails.removeStudent. Only present while the collection is ACTIVE. */
  async removeStudent(studentFullName: string): Promise<void> {
    this.page.once('dialog', (dialog) => dialog.accept());
    await this.requirements.removeButton(studentFullName).click();
  }

  /** Puts a student back into an ACTIVE collection - see AddStudentToCollectionUseCase: no
   *  confirmation dialog (unlike removeStudent), since it's the safe/additive direction. */
  async addStudentBack(studentFullName: string): Promise<void> {
    await this.requirements.addButton(studentFullName).click();
  }

  /** Treasurer-only - the "Add photo or file" control only exists inside the
   *  `isCollectionDetails` branch (see CollectionAttachmentResource's javadoc). Uploads via
   *  the real presigned-URL flow (request-url -> PUT to S3/Localstack -> confirm), not a
   *  mock - the hidden <input type="file"> still accepts setInputFiles even though it's
   *  never visible on screen (Playwright doesn't require visibility for that action). */
  async uploadAttachment(filePath: string): Promise<void> {
    await this.page.locator('input[type="file"]').setInputFiles(filePath);
    // Waits for the button to leave its "Uploading..." state - the full three-call upload
    // flow (request-url, PUT to S3, confirm) needs to actually finish before the caller's
    // next assertion looks for the new attachment in the (now-reloaded) list.
    await expect(this.page.getByRole('button', { name: 'Wysyłanie...' })).toHaveCount(0);
  }

  async expectAttachmentVisible(fileName: string): Promise<void> {
    await expect(this.page.locator('.attachment-list li', { hasText: fileName })).toBeVisible();
  }

  async expectAttachmentCount(count: number): Promise<void> {
    await expect(this.page.locator('.attachment-list li')).toHaveCount(count);
  }

  async expectUploadAttachmentButtonAbsent(): Promise<void> {
    await expect(this.page.getByRole('button', { name: 'Dodaj zdjęcie lub plik' })).toHaveCount(0);
  }

  async expectDeleteAttachmentButtonAbsent(): Promise<void> {
    await expect(this.page.locator('.attachment-list').getByRole('button')).toHaveCount(0);
  }

  /** Accepts the confirm() dialog, same pattern as removeStudent. */
  async deleteAttachment(fileName: string): Promise<void> {
    this.page.once('dialog', (dialog) => dialog.accept());
    await this.page.locator('.attachment-list li', { hasText: fileName }).getByRole('button').click();
  }
}

export class GlobalLedgerPage {
  readonly activityLog: EntryList;

  constructor(private readonly page: Page) {
    this.activityLog = new EntryList(page.getByTestId('ledger-entry'));
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
    this.activityLog = new EntryList(page.getByTestId('ledger-entry'));
  }

  async goto(studentId: string): Promise<void> {
    await this.page.goto(`/students/${studentId}`);
  }

  async expectStudentName(fullName: string): Promise<void> {
    await expect(this.page.locator('h1')).toContainText(fullName);
  }

  async expectBalance(amountZl: string): Promise<void> {
    await expect(this.page.getByTestId('piggy-balance')).toHaveText(`${amountZl}zł`);
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

  /** Same idea as studentIdByLastName, for a collection created through the UI. */
  async collectionIdByTitle(title: string): Promise<string> {
    const resp = await this.request.get(`${this.baseURL}/api/collections`, { headers: this.headers });
    const collections = (await resp.json()) as Array<{ id: string; title: string }>;
    const match = collections.find((c) => c.title === title);
    if (!match) {
      throw new Error(`No collection titled "${title}" found via GET /api/collections`);
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
