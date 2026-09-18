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

export interface SeededTreasurer {
  studentId: string;
  parentId: string;
}

/** Seeds a Student + a TREASURER Parent linked to it, matching the real shape
 *  ParentDynamoDbAdapter/StudentDynamoDbAdapter read - see those classes' `fromItem`. */
export function seedTreasurer(
  email: string,
  firstName: string,
  lastName: string,
  studentFirstName: string,
  studentLastName: string,
): SeededTreasurer {
  const studentId = randomUUID();
  const parentId = randomUUID();

  putItem('groszdogrosza', {
    pk: { S: `STUDENT#${studentId}` },
    sk: { S: 'STUDENT' },
    id: { S: studentId },
    firstName: { S: studentFirstName },
    lastName: { S: studentLastName },
    piggyBankBalance: { N: '0' },
  });

  putItem('groszdogrosza', {
    pk: { S: `PARENT#${parentId}` },
    sk: { S: 'PARENT' },
    id: { S: parentId },
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
