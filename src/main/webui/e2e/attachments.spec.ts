import path from 'node:path';
import { test, expect } from '@playwright/test';
import { seedTreasurer } from './seed';
import { Api, CollectionDetailsPage, Dashboard, LoginPage, TreasurerPanel } from './pages';

const RECEIPT_PNG = path.join(__dirname, 'fixtures/receipt.png');
const INVOICE_PDF = path.join(__dirname, 'fixtures/invoice.pdf');

/**
 * Photos/receipts attached to a collection to document what the collected money was spent
 * on - see CLAUDE.md, "Collection attachments". Exercises the real direct-to-S3 flow (a
 * presigned PUT against the Dev Services Localstack bucket, not a mock), and both halves of
 * the access rule the user asked for: any authenticated parent can VIEW a collection's
 * attachments, but only the treasurer can upload/delete them.
 */
test.describe('collection details: attachments', () => {
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

  test('a treasurer can upload and delete attachments; a regular parent can only view them', async ({
    page,
    request,
    baseURL,
  }) => {
    const unique = Date.now();
    const studentLastName = `Zalacznik${unique}`;
    const collectionTitle = `Prezent dla nauczyciela ${unique}`;

    const login = new LoginPage(page);
    const treasurer = new TreasurerPanel(page);
    const dashboard = new Dashboard(page);

    const seeded = seedTreasurer('skarbnik@example.com', 'Jarek', 'Skarbnik', 'Jasio', 'Skarbnik');
    jasioStudentId = seeded.studentId;

    await login.loginAs('skarbnik', 'skarbnik');
    const treasurerIdToken = await page.evaluate(() => sessionStorage.getItem('id_token'));
    expect(treasurerIdToken, 'expected an id_token in sessionStorage right after login').toBeTruthy();
    api = new Api(request, baseURL!, treasurerIdToken!);

    await treasurer.goto();
    const zosia = await treasurer.addStudent('Zosia', studentLastName);
    await zosia.addParent({
      firstName: 'Anna',
      lastName: studentLastName,
      email: 'anna.testowa@example.com',
      expectedSenderName: `Anna ${studentLastName}`,
    });
    zosiaStudentId = await api.studentIdByLastName(studentLastName);

    await treasurer.createCollection(collectionTitle, 'Zbiórka na prezent', '50');
    await dashboard.goto();
    await dashboard.openCollection(collectionTitle);
    const collection = new CollectionDetailsPage(page);
    // Captured now, not read lazily later - collection.id parses the CURRENT page URL (see
    // its getter), which stops being /collections/<id> the moment we log out below.
    const collectionId = collection.id;

    // --- Treasurer's view: no documents yet, then upload one of each allowed type ---
    await collection.expectAttachmentCount(0);

    await collection.uploadAttachment(RECEIPT_PNG);
    await collection.expectAttachmentVisible('receipt.png');
    await collection.expectAttachmentCount(1);

    await collection.uploadAttachment(INVOICE_PDF);
    await collection.expectAttachmentVisible('invoice.pdf');
    await collection.expectAttachmentCount(2);

    // Survives a reload - proves the upload was actually persisted (metadata in DynamoDB,
    // bytes in S3/Localstack), not just held in the page's in-memory state.
    await collection.reload();
    await collection.expectAttachmentCount(2);

    await collection.deleteAttachment('receipt.png');
    await collection.expectAttachmentCount(1);
    await collection.expectAttachmentVisible('invoice.pdf');

    // --- A regular parent viewing the same collection: sees the remaining document, but
    //     has no way to upload or delete one ---
    await page.getByRole('button', { name: 'Log out' }).click();
    await page.waitForLoadState('networkidle');
    await login.loginAs('rodzic1', 'rodzic1');
    await page.goto(`/collections/${collectionId}`);
    await expect(page.locator('.percent')).toBeVisible(); // confirms we're on the real page, not a 404

    await collection.expectAttachmentVisible('invoice.pdf');
    await collection.expectUploadAttachmentButtonAbsent();
    await collection.expectDeleteAttachmentButtonAbsent();
  });
});
