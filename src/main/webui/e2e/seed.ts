import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';

/**
 * Bootstraps the one thing the app genuinely cannot create through its own UI: the very
 * first treasurer record (see CLAUDE.md, "Bootstrap gap, not yet solved" - POST
 * /api/students/{id}/parents is treasurer-only, so nothing can create the first treasurer
 * except writing directly to the table). Everything else in main-flow.spec.ts goes through
 * the real UI/API, not this file.
 *
 * Finds the Dev Services Localstack container by image name rather than a fixed port -
 * Quarkus Dev Services always picks a random host port, so there's no static endpoint to
 * hardcode. This only works against the LOCAL dev DynamoDB (Localstack, dummy credentials) -
 * never point GG_E2E_BASE_URL at a real deployment and run this against it.
 */
function localstackEndpoint(): string {
  // Set when more than one Localstack is running (e.g. another project's dev server next to
  // this one) - the first container `docker ps` lists may not be this app's.
  if (process.env.GG_LOCALSTACK_URL) {
    return process.env.GG_LOCALSTACK_URL;
  }
  const psOutput = execFileSync('docker', ['ps', '--format', '{{.Image}}\t{{.Ports}}']).toString();
  const line = psOutput.split('\n').find((l) => l.startsWith('localstack/localstack'));
  if (!line) {
    throw new Error(
      'No running localstack container found - is `./mvnw quarkus:dev` running (it starts Dev Services automatically)?',
    );
  }
  const match = line.match(/0\.0\.0\.0:(\d+)->4566\/tcp/);
  if (!match) {
    throw new Error(`Could not parse the Localstack host port from: ${line}`);
  }
  return `http://localhost:${match[1]}`;
}

function putItem(tableName: string, item: unknown): void {
  execFileSync(
    'aws',
    [
      'dynamodb',
      'put-item',
      '--table-name',
      tableName,
      '--item',
      JSON.stringify(item),
      '--endpoint-url',
      localstackEndpoint(),
      '--region',
      'eu-central-1',
    ],
    { env: { ...process.env, AWS_ACCESS_KEY_ID: 'test', AWS_SECRET_ACCESS_KEY: 'test' } },
  );
}

function deleteItem(tableName: string, key: unknown): void {
  execFileSync(
    'aws',
    ['dynamodb', 'delete-item', '--table-name', tableName, '--key', JSON.stringify(key),
      '--endpoint-url', localstackEndpoint(), '--region', 'eu-central-1'],
    { env: { ...process.env, AWS_ACCESS_KEY_ID: 'test', AWS_SECRET_ACCESS_KEY: 'test' } },
  );
}

/** The class every spec works in - the same id application.properties uses as the dev
 *  default class (`%dev.groszdogrosza.public.default-class-id`), so "/" shows it too. */
export const E2E_CLASS_ID = 'dev-class';

/** Creates (or re-creates, idempotently) a class with one treasurer membership. */
export function seedClass(classId: string, name: string, treasurerEmail: string, publicToken: string): void {
  putItem('groszdogrosza', {
    pk: { S: `CLASS#${classId}` },
    sk: { S: 'CLASS' },
    id: { S: classId },
    classId: { S: classId },
    name: { S: name },
    publicToken: { S: publicToken },
    bankAccountNumber: { NULL: true },
    blikPhoneNumber: { NULL: true },
    createdAt: { S: new Date().toISOString() },
  });
  putItem('groszdogrosza', {
    pk: { S: `CLASS#${classId}` },
    sk: { S: `TREASURER#${treasurerEmail.toLowerCase()}` },
    classId: { S: classId },
    email: { S: treasurerEmail.toLowerCase() },
  });
}

/** Removes a class and its treasurer membership (not its students - delete those via the API). */
export function deleteClass(classId: string, treasurerEmail: string): void {
  deleteItem('groszdogrosza', { pk: { S: `CLASS#${classId}` }, sk: { S: `TREASURER#${treasurerEmail.toLowerCase()}` } });
  deleteItem('groszdogrosza', { pk: { S: `CLASS#${classId}` }, sk: { S: 'CLASS' } });
}

export interface SeededTreasurer {
  studentId: string;
  parentId: string;
}

/** Seeds the test class with `email` as its treasurer, plus the treasurer's own child (a
 *  Student) and parent record, matching the real shape the DynamoDB adapters read. */
export function seedTreasurer(
  email: string,
  firstName: string,
  lastName: string,
  studentFirstName: string,
  studentLastName: string,
  classId: string = E2E_CLASS_ID,
): SeededTreasurer {
  const studentId = randomUUID();
  const parentId = randomUUID();
  seedClass(classId, classId === E2E_CLASS_ID ? 'Klasa testowa' : classId, email, `${classId}-public-link`);

  putItem('groszdogrosza', {
    pk: { S: `STUDENT#${studentId}` },
    sk: { S: 'STUDENT' },
    id: { S: studentId },
    classId: { S: classId },
    firstName: { S: studentFirstName },
    lastName: { S: studentLastName },
    piggyBankBalance: { N: '0' },
  });

  putItem('groszdogrosza', {
    pk: { S: `PARENT#${parentId}` },
    sk: { S: 'PARENT' },
    id: { S: parentId },
    classId: { S: classId },
    studentId: { S: studentId },
    firstName: { S: firstName },
    lastName: { S: lastName },
    email: { S: email },
    expectedSenderName: { S: `${firstName} ${lastName}` },
    cognitoSubjectId: { NULL: true },
    role: { S: 'TREASURER' },
    bankAccountNumber: { NULL: true },
    blikPhoneNumber: { NULL: true },
  });

  return { studentId, parentId };
}
