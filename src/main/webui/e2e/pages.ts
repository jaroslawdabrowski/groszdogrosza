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

  /** A student truly absent from the table altogether - not even shown greyed out. Since
   *  CollectionDetails.rosterRows merges in the full roster while ACTIVE (see
   *  expectNotIncluded below), this now only really applies to a SETTLED collection, where
   *  the table collapses back to just the real historical requirements. */
  async expectAbsent(studentFullName: string): Promise<void> {
    await expect(this.table).not.toContainText(studentFullName);
  }

  /** A student NOT part of this collection (never added, or removed earlier) still shows up
   *  as a row while the collection is ACTIVE - greyed out, with dashes instead of amounts and
   *  an "add back" action instead of "remove" (see CollectionDetails.rosterRows). */
  async expectNotIncluded(studentFullName: string): Promise<void> {
    const row = this.row(studentFullName);
    await expect(row.locator('td').nth(1)).toHaveText('–');
    await expect(row.locator('td').nth(2)).toHaveText('–');
    await expect(row.locator('td').nth(3)).toContainText('Nie w zbiórce');
  }

  /** The 5th ("actions") column only exists while the collection is ACTIVE - see
   *  CollectionDetails.visibleRequirementColumns. */
  removeButton(studentFullName: string): Locator {
    return this.row(studentFullName).getByRole('button');
  }

  /** The counterpart to removeButton, for a row currently shown as not-included (see
   *  expectNotIncluded) - same single-button-in-the-row pattern. */
  addButton(studentFullName: string): Locator {
    return this.row(studentFullName).getByRole('button');
  }
}

/** The "Rozliczenie zbiórki" card's own at-a-glance summary (paid-count, collected, expected)
 *  plus the actual-cost input it auto-fills - see CollectionDetails.paidStudentsCount /
 *  totalStudentsCount / totalExpected and reload()'s actualCostSpent pre-fill. Scoped to this
 *  specific card (matched by its title) since the post-settle "Wynik rozliczenia" card reuses
 *  the same `.summary-stat` class for its own, differently-shaped numbers. */
export class SettleSummary {
  constructor(private readonly root: Locator) {}

  async expectStudentsPaid(paid: number, total: number): Promise<void> {
    await expect(this.root.locator('.summary-stat').nth(0)).toContainText(`${paid} / ${total}`);
  }

  async expectTotalCollected(amountZl: string): Promise<void> {
    await expect(this.root.locator('.summary-stat').nth(1)).toContainText(`${amountZl} zł`);
  }

  async expectTotalExpected(amountZl: string): Promise<void> {
    await expect(this.root.locator('.summary-stat').nth(2)).toContainText(`${amountZl} zł`);
  }

  /** Only rendered at all once at least one student has been removed from this collection -
   *  see CollectionDetails.removedStudentsCount's javadoc for why 0 is deliberately hidden
   *  rather than shown as "0". */
  async expectRemovedStudentsCount(count: number): Promise<void> {
    await expect(this.root.locator('.summary-stat').nth(3)).toContainText(String(count));
  }

  async expectRemovedStudentsCountAbsent(): Promise<void> {
    await expect(this.root.locator('.summary-stat')).toHaveCount(3);
  }

  /** The cost input starts pre-filled with what's actually been collected so far (see
   *  CollectionDetails.reload), not left at 0 for the treasurer to fill in by hand. */
  async expectActualCostPrefilled(amountZl: string): Promise<void> {
    await expect(this.root.locator('input[type="number"]')).toHaveValue(amountZl);
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

  /** The "does my child take part / have they paid" icon on a collection's card - see
   *  MyStudentStatusBadge. `icon` is the mat-icon ligature name (e.g. 'check_circle'), not
   *  the translated tooltip text (icon-only buttons/badges expose no accessible name). */
  async expectMyStudentStatus(title: string, icon: string): Promise<void> {
    const card = this.page.locator('mat-card').filter({ hasText: title });
    await expect(card.locator('app-my-student-status-badge mat-icon')).toHaveText(icon);
  }

  /** No logged-in parent resolvable to a child - see MyStudentStatusBadge, which renders
   *  nothing at all in that case rather than an empty/disabled icon. */
  async expectMyStudentStatusAbsent(title: string): Promise<void> {
    const card = this.page.locator('mat-card').filter({ hasText: title });
    await expect(card.locator('app-my-student-status-badge mat-icon')).toHaveCount(0);
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

  /** Same badge as Dashboard.expectMyStudentStatus - see MyStudentStatusBadge. The public
   *  endpoint still resolves it for a logged-in visitor (authInterceptor attaches the bearer
   *  token even to this unauthenticated call - see backend PublicOverviewResource's javadoc). */
  async expectMyStudentStatus(title: string, icon: string): Promise<void> {
    const card = this.page.locator('mat-card').filter({ hasText: title });
    await expect(card.locator('app-my-student-status-badge mat-icon')).toHaveText(icon);
  }

  /** For an anonymous visitor (or a logged-in one with no linked child), no badge at all. */
  async expectMyStudentStatusAbsent(title: string): Promise<void> {
    const card = this.page.locator('mat-card').filter({ hasText: title });
    await expect(card.locator('app-my-student-status-badge mat-icon')).toHaveCount(0);
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

  /** The piggy bank balance chip shown under this student's name - see
   *  treasurer-panel.html's `.student-balance`. Uses `toContainText`, not `toHaveText` -
   *  the chip also renders a mat-icon, whose ligature name ("account_balance_wallet") is
   *  part of the element's own text content. */
  async expectPiggyBankBalance(amountZl: string): Promise<void> {
    await expect(this.root.locator('.student-balance')).toContainText(`${amountZl} zł`);
  }

  /** Manual cash top-up ("ktoś dał mi gotówkę") - treasurer-only, credit-only (see
   *  TreasurerPanel.creditPiggyBank's javadoc: adds to the balance, never replaces it).
   *  Behind the row's single "more" (⋮) menu, not its own icon button - see the comment on
   *  `.student-actions` in treasurer-panel.html for why. The menu's own panel is a CDK
   *  overlay portaled to the document body, not a descendant of this row, so the item has
   *  to be located from the page, not `this.root`. */
  async creditPiggyBank(amountZl: string): Promise<void> {
    await this.root.locator('.student-actions button').click();
    await this.root.page().getByRole('menuitem', { name: 'Doładuj skarbonkę gotówką' }).click();
    const form = this.root.locator('.credit-form');
    await form.getByLabel('Kwota (zł)').fill(amountZl);
    await form.getByRole('button', { name: 'Doładuj skarbonkę' }).click();
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
  readonly settleSummary: SettleSummary;

  constructor(private readonly page: Page) {
    this.requirements = new RequirementsTable(page.locator('table'));
    this.contributions = new EntryList(page.locator('.contribution-list'));
    this.settlementResult = new EntryList(page.locator('.settlement-list'));
    this.settleSummary = new SettleSummary(page.locator('mat-card').filter({ hasText: 'Rozliczenie zbiórki' }));
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

  /** The "does my child take part / have they paid" icon shown on a non-treasurer's own
   *  progress card (see MyStudentStatusBadge) - `icon` is the mat-icon ligature name, not
   *  the translated tooltip text. Same pattern as Dashboard/PublicOverviewPage's own
   *  expectMyStudentStatus, just scoped to this page's single card instead of a list of them. */
  async expectMyStudentStatus(icon: string): Promise<void> {
    await expect(this.page.locator('app-my-student-status-badge mat-icon')).toHaveText(icon);
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
   *  back-link, the settle form, and the "remove student" action column - is hidden once
   *  print CSS applies (see the `.no-print` rules in styles.scss/collection-details.scss). */
  async expectInteractiveChromeHiddenForPrint(): Promise<void> {
    await expect(this.page.locator('.app-toolbar')).toBeHidden();
    await expect(this.page.locator('.back-link')).toBeHidden();
    await expect(this.page.locator('.mat-column-actions').first()).toBeHidden();
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
    await expect(this.page.locator('.attachment-list .delete-button')).toHaveCount(0);
  }

  /** Accepts the confirm() dialog, same pattern as removeStudent. Icon-only button, no
   *  accessible name (matTooltip text isn't exposed as one) - grab the row's one button, same
   *  pattern as RequirementsTable.removeButton. */
  async deleteAttachment(fileName: string): Promise<void> {
    this.page.once('dialog', (dialog) => dialog.accept());
    await this.page.locator('.attachment-list li', { hasText: fileName }).getByRole('button').click();
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
