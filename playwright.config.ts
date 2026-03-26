import { defineConfig, devices } from '@playwright/test';

const baseURL = process.env.SALEADS_ENTRY_URL ?? process.env.BASE_URL;

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  retries: 0,
  workers: 1,
  timeout: 5 * 60 * 1000,
  expect: {
    timeout: 20_000,
  },
  reporter: [['line'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    ignoreHTTPSErrors: true,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
