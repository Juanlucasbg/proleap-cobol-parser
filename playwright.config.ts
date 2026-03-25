import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e/saleads',
  timeout: 120_000,
  expect: {
    timeout: 15_000,
  },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list']],
  use: {
    headless: process.env.HEADED !== 'true',
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20_000,
    navigationTimeout: 30_000,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
