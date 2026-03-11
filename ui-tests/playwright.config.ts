import { defineConfig } from '@playwright/test';

const baseURL =
  process.env.SALEADS_BASE_URL ||
  process.env.BASE_URL ||
  process.env.APP_URL;

export default defineConfig({
  testDir: './tests',
  timeout: 180_000,
  expect: {
    timeout: 20_000,
  },
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    browserName: 'chromium',
    headless: process.env.HEADED !== 'true',
    viewport: { width: 1440, height: 900 },
    trace: 'on-first-retry',
    video: 'retain-on-failure',
  },
  outputDir: 'test-results',
});
