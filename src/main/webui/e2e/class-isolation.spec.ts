import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { E2E_CLASS_ID, deleteClass, seedTreasurer } from './seed';
import { AppShell, LoginPage } from './pages';

/**
 * The security core of multiple classes (docs/MULTI-CLASS.md, "Isolation between classes"):
 * nobody can see or change anything in a class they're not a member of, whatever they try.
 *
 * Two classes: A (the usual test class, treasurer "skarbnik", with rodzic1 as a parent of one
 * child) and B (treasurer rodzic2). Then:
 *  - members of A call EVERY endpoint of class B and must get 404 each time - as if B didn't
 *    exist (not 403, which would confirm it does);
 *  - A's treasurer pastes B's ids into A's own URLs (the classic IDOR attempt) - 404, and B's
 *    data is untouched afterwards;
 *  - a parent in A can't do treasurer things in A (403) or see another family's child (404);
 *  - B's public link, opened while logged in as A's treasurer, shows only totals - exactly
 *    what an anonymous visitor sees.
 */
test.describe('class isolation', () => {
  const unique = Date.now();
  const classB = `iso-b-${unique}`;
  const api = (path: string) => `/api/classes/${path}`;

  let tokens: { skarbnik: string; rodzic1: string; rodzic2: string };
  let ids: {
    aStudent: string;
    aChildOfRodzic1: string;
    aOtherChild: string;
    aCollection: string;
    bStudent: string;
    bParent: string;
    bCollection: string;
  };
  /** Undo steps, run in afterAll with ITS OWN request context - the one beforeAll gets is
   *  disposed when beforeAll ends, so using it later fails (silently, in a cleanup). */
  const cleanup: Array<(request: APIRequestContext) => Promise<void>> = [];

  async function tokenFor(page: Page, user: string): Promise<string> {
    await new LoginPage(page).loginAs(user, user);
    const token = await page.evaluate(() => localStorage.getItem('id_token'));
    expect(token, `id_token for ${user}`).toBeTruthy();
    await new AppShell(page).logout();
    return token!;
  }

  async function call(request: APIRequestContext, token: string, method: string, path: string, data?: unknown) {
    return request.fetch(path, { method, data, headers: { Authorization: `Bearer ${token}` } });
  }

  async function json<T>(request: APIRequestContext, token: string, method: string, path: string, data?: unknown): Promise<T> {
    const response = await call(request, token, method, path, data);
    expect(response.ok(), `${method} ${path} -> ${response.status()} ${await response.text()}`).toBeTruthy();
    return (await response.json()) as T;
  }

  test.beforeAll(async ({ browser, request }) => {
    test.setTimeout(120_000);
    const a = seedTreasurer('skarbnik@example.com', 'Jarek', 'Skarbnik', 'Jasio', `Izolacja${unique}`);
    const b = seedTreasurer('piotr.testowy@example.com', 'Piotr', 'Testowy', 'Bartek', `Obcy${unique}`, classB);
    cleanup.push(async () => deleteClass(classB, 'piotr.testowy@example.com'));

    const page = await browser.newPage();
    tokens = {
      skarbnik: await tokenFor(page, 'skarbnik'),
      rodzic1: await tokenFor(page, 'rodzic1'),
      rodzic2: await tokenFor(page, 'rodzic2'),
    };
    await page.close();

    // Class A: rodzic1 is the parent of one child; another child has nobody.
    const kid = await json<{ id: string }>(request, tokens.skarbnik, 'POST', api(`${E2E_CLASS_ID}/students`),
      { firstName: 'Ola', lastName: `Rodzinna${unique}` });
    await json(request, tokens.skarbnik, 'POST', api(`${E2E_CLASS_ID}/students/${kid.id}/parents`),
      { firstName: 'Anna', lastName: 'Testowa', email: 'anna.testowa@example.com', expectedSenderName: 'Anna Testowa' });
    const other = await json<{ id: string }>(request, tokens.skarbnik, 'POST', api(`${E2E_CLASS_ID}/students`),
      { firstName: 'Ela', lastName: `Sasiadka${unique}` });
    const aCollection = await json<{ id: string }>(request, tokens.skarbnik, 'POST', api(`${E2E_CLASS_ID}/collections`),
      { title: `Izolacja A ${unique}`, description: '', baseAmountPerStudent: 10, studentIds: [a.studentId, kid.id, other.id] });
    for (const id of [a.studentId, kid.id, other.id]) {
      cleanup.push(async (req) => {
        await call(req, tokens.skarbnik, 'DELETE', api(`${E2E_CLASS_ID}/students/${id}`));
      });
    }

    // Class B: its treasurer creates a collection.
    const bCollection = await json<{ id: string }>(request, tokens.rodzic2, 'POST', api(`${classB}/collections`),
      { title: `Obca zbiórka ${unique}`, description: 'Tajne', baseAmountPerStudent: 25, studentIds: [b.studentId] });
    cleanup.push(async (req) => {
      await call(req, tokens.rodzic2, 'DELETE', api(`${classB}/students/${b.studentId}`));
    });

    ids = {
      aStudent: a.studentId,
      aChildOfRodzic1: kid.id,
      aOtherChild: other.id,
      aCollection: aCollection.id,
      bStudent: b.studentId,
      bParent: b.parentId,
      bCollection: bCollection.id,
    };
  });

  test.afterAll(async ({ request }) => {
    for (const step of cleanup.reverse()) {
      await step(request);
    }
  });

  /** Every endpoint of a class, with the ids of things that exist in class B. */
  function everyEndpointOf(classId: string): Array<[string, string, unknown?]> {
    const c = (path: string) => api(`${classId}${path}`);
    return [
      ['GET', c('')],
      ['GET', c('/overview')],
      ['PUT', c('/payment-info'), { bankAccountNumber: '00', blikPhoneNumber: '00' }],
      ['GET', c('/students')],
      ['POST', c('/students'), { firstName: 'X', lastName: 'Y' }],
      ['GET', c(`/students/${ids.bStudent}`)],
      ['PUT', c(`/students/${ids.bStudent}`), { firstName: 'X', lastName: 'Y' }],
      ['DELETE', c(`/students/${ids.bStudent}`)],
      ['POST', c(`/students/${ids.bStudent}/parents`), { firstName: 'X', lastName: 'Y', email: 'x@y.z', expectedSenderName: 'X' }],
      ['POST', c(`/students/${ids.bStudent}/piggy-bank/credit`), { amount: 100 }],
      ['GET', c(`/students/${ids.bStudent}/ledger`)],
      ['GET', c(`/parents/${ids.bParent}`)],
      ['PUT', c(`/parents/${ids.bParent}`), { firstName: 'X', lastName: 'Y', email: 'x@y.z', expectedSenderName: 'X' }],
      ['DELETE', c(`/parents/${ids.bParent}`)],
      ['POST', c(`/parents/${ids.bParent}/cognito-account`)],
      ['GET', c('/collections')],
      ['POST', c('/collections'), { title: 'X', description: '', baseAmountPerStudent: 1, studentIds: [ids.bStudent] }],
      ['GET', c(`/collections/${ids.bCollection}`)],
      ['POST', c(`/collections/${ids.bCollection}/contributions`), { studentId: ids.bStudent, amount: 5 }],
      ['POST', c(`/collections/${ids.bCollection}/settle-preview`), { actualCostSpent: 1 }],
      ['POST', c(`/collections/${ids.bCollection}/settle`), { actualCostSpent: 1 }],
      ['DELETE', c(`/collections/${ids.bCollection}/students/${ids.bStudent}`)],
      ['POST', c(`/collections/${ids.bCollection}/students/${ids.bStudent}`)],
      ['GET', c(`/collections/${ids.bCollection}/attachments`)],
      ['POST', c(`/collections/${ids.bCollection}/attachments/upload-url`), { fileName: 'a.jpg', contentType: 'image/jpeg', sizeBytes: 10 }],
      ['GET', c('/ledger')],
    ];
  }

  test('members of another class get 404 on every endpoint of class B', async ({ request }) => {
    for (const user of ['skarbnik', 'rodzic1'] as const) {
      for (const [method, path, data] of everyEndpointOf(classB)) {
        const response = await call(request, tokens[user], method, path, data);
        expect(response.status(), `${user}: ${method} ${path}`).toBe(404);
      }
    }
  });

  test('without logging in, class endpoints are refused outright', async ({ request }) => {
    for (const [method, path, data] of everyEndpointOf(classB)) {
      const response = await request.fetch(path, { method, data });
      expect(response.status(), `anonymous: ${method} ${path}`).toBe(401);
    }
  });

  test("class B's ids pasted into class A's own URLs are not found, and B's data is untouched", async ({ request }) => {
    const a = (path: string) => api(`${E2E_CLASS_ID}${path}`);
    const attempts: Array<[string, string, unknown?]> = [
      ['GET', a(`/students/${ids.bStudent}`)],
      ['GET', a(`/students/${ids.bStudent}/ledger`)],
      ['PUT', a(`/students/${ids.bStudent}`), { firstName: 'Hacked', lastName: 'Hacked' }],
      ['POST', a(`/students/${ids.bStudent}/piggy-bank/credit`), { amount: 1000 }],
      ['POST', a(`/students/${ids.bStudent}/parents`), { firstName: 'X', lastName: 'Y', email: 'x@y.z', expectedSenderName: 'X' }],
      ['GET', a(`/parents/${ids.bParent}`)],
      ['PUT', a(`/parents/${ids.bParent}`), { firstName: 'Hacked', lastName: 'Hacked', email: 'h@h.h', expectedSenderName: 'H' }],
      ['GET', a(`/collections/${ids.bCollection}`)],
      ['POST', a(`/collections/${ids.bCollection}/contributions`), { studentId: ids.aStudent, amount: 5 }],
      ['POST', a(`/collections/${ids.bCollection}/settle`), { actualCostSpent: 1 }],
      ['GET', a(`/collections/${ids.bCollection}/attachments`)],
      // A's own collection, but booking money for B's child:
      ['POST', a(`/collections/${ids.aCollection}/contributions`), { studentId: ids.bStudent, amount: 5 }],
      ['POST', a(`/collections/${ids.aCollection}/students/${ids.bStudent}`)],
    ];
    for (const [method, path, data] of attempts) {
      const response = await call(request, tokens.skarbnik, method, path, data);
      expect(response.status(), `skarbnik: ${method} ${path}`).toBe(404);
    }
    // Deletes are "not found" too, and must not have deleted anything.
    for (const path of [a(`/students/${ids.bStudent}`), a(`/parents/${ids.bParent}`)]) {
      const response = await call(request, tokens.skarbnik, 'DELETE', path);
      expect(response.status(), `skarbnik: DELETE ${path}`).toBe(404);
    }

    // Class B, seen by its own treasurer, is exactly as it was.
    const student = await json<{ firstName: string; piggyBankBalance: number; parents: unknown[] }>(
      request, tokens.rodzic2, 'GET', api(`${classB}/students/${ids.bStudent}`));
    expect(student.firstName).toBe('Bartek');
    expect(student.piggyBankBalance).toBe(0);
    expect(student.parents).toHaveLength(1);
    const collection = await json<{ contributions: unknown[]; collection: { status: string } }>(
      request, tokens.rodzic2, 'GET', api(`${classB}/collections/${ids.bCollection}`));
    expect(collection.contributions).toHaveLength(0);
    expect(collection.collection.status).toBe('ACTIVE');
    const ledger = await json<unknown[]>(request, tokens.rodzic2, 'GET', api(`${classB}/students/${ids.bStudent}/ledger`));
    expect(ledger).toHaveLength(0);
  });

  test('a parent in class A can do no treasurer things and sees no other family', async ({ request }) => {
    const a = (path: string) => api(`${E2E_CLASS_ID}${path}`);
    for (const [method, path, data] of [
      ['GET', a('/students')],
      ['POST', a('/students'), { firstName: 'X', lastName: 'Y' }],
      ['GET', a('/ledger')],
      ['POST', a('/collections'), { title: 'X', description: '', baseAmountPerStudent: 1, studentIds: [ids.aOtherChild] }],
      ['POST', a(`/collections/${ids.aCollection}/settle`), { actualCostSpent: 1 }],
      ['PUT', a('/payment-info'), { bankAccountNumber: '00', blikPhoneNumber: '00' }],
    ] as Array<[string, string, unknown?]>) {
      const response = await call(request, tokens.rodzic1, method, path, data);
      expect(response.status(), `rodzic1: ${method} ${path}`).toBe(403);
    }
    for (const [method, path] of [
      ['GET', a(`/students/${ids.aOtherChild}`)],
      ['GET', a(`/students/${ids.aOtherChild}/ledger`)],
      ['DELETE', a(`/collections/${ids.aCollection}/students/${ids.aOtherChild}`)],
    ]) {
      const response = await call(request, tokens.rodzic1, method, path);
      expect(response.status(), `rodzic1: ${method} ${path}`).toBe(404);
    }
    // Their own child is fine.
    const own = await call(request, tokens.rodzic1, 'GET', a(`/students/${ids.aChildOfRodzic1}`));
    expect(own.status()).toBe(200);
  });

  test("class B's public link shows only totals, even to a logged-in member of another class", async ({ request }) => {
    const path = `/api/public/classes/${classB}-public-link/overview`;
    const anonymous = await (await request.get(path)).json();
    const asOtherTreasurer = await (await call(request, tokens.skarbnik, 'GET', path)).json();
    expect(asOtherTreasurer).toEqual(anonymous);
    expect(anonymous.activeCollections).toHaveLength(1);
    expect(anonymous.activeCollections[0].collection.myStudentStatus ?? null).toBeNull();
    // Totals only - no per-student breakdown at all.
    expect(JSON.stringify(anonymous)).not.toContain(ids.bStudent);
    expect(JSON.stringify(anonymous)).not.toContain('Bartek');
  });

  test('/api/me lists only your own classes', async ({ request }) => {
    const skarbnikClasses = (await json<{ classes: Array<{ id: string }> }>(request, tokens.skarbnik, 'GET', '/api/me'))
      .classes.map((c) => c.id);
    expect(skarbnikClasses).toContain(E2E_CLASS_ID);
    expect(skarbnikClasses).not.toContain(classB);
    const rodzic1Classes = (await json<{ classes: Array<{ id: string; role: string }> }>(request, tokens.rodzic1, 'GET', '/api/me'))
      .classes;
    expect(rodzic1Classes.map((c) => c.id)).not.toContain(classB);
    expect(rodzic1Classes.find((c) => c.id === E2E_CLASS_ID)?.role).toBe('PARENT');
  });
});
