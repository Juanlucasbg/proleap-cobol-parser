import { defineConfig } from '@playwright/test';

const isCi = Boolean(process.env.CI);

export default defineConfig({
  testDir: './tests',
  timeout: 180_000,
  expect: {
    timeout: 20_000
  },
  fullyParallel: false,
  retries: isCi ? 1 : 0,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }]
  ],
  use: {
    headless: isCi,
    viewport: { width: 1440, height: 960 },
    actionTimeout: 20_000,
    navigationTimeout: 45_000,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure'
  }
});
