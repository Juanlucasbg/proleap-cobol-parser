import { defineConfig } from '@playwright/test';

const baseURL = process.env.SALEADS_START_URL ?? process.env.SALEADS_BASE_URL ?? process.env.BASE_URL;

export default defineConfig({
  testDir: './tests',
  timeout: 4 * 60 * 1000,
  expect: {
    timeout: 20 * 1000
  },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: 'playwright-report' }],
    ['json', { outputFile: 'test-results/results.json' }]
  ],
  use: {
    baseURL,
    headless: process.env.PW_HEADLESS === 'false' ? false : true,
    ignoreHTTPSErrors: true,
    viewport: { width: 1440, height: 900 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'retain-on-failure'
  }
});
