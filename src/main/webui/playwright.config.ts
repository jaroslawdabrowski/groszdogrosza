import { defineConfig, devices } from '@playwright/test';

/**
 * Requires `./mvnw quarkus:dev` already running (see e2e/README.md) - this suite doesn't
 * start the server itself, since Dev Services (Localstack DynamoDB + Keycloak) take ~60s to
 * boot and the seeding helper needs to discover the already-running Localstack container's
 * port (see e2e/seed.ts).
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  fullyParallel: false,
  // Every spec in this suite seeds the same bootstrap-gap treasurer under the same email
  // (see seed.ts) and shares one real backend/DynamoDB table - two spec FILES running as
  // separate workers at the same time collide on that shared state (AuthorizationSupport's
  // findByEmail scan has no ordering guarantee - see main-flow.spec.ts's class comment) and
  // also just overload the single dev server enough to blow past the 30s test timeout.
  // `fullyParallel: false` alone only serializes tests within one file; workers: 1 is what
  // actually forces every spec file to run one at a time too.
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env.GG_E2E_BASE_URL ?? 'http://localhost:8080',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
