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
