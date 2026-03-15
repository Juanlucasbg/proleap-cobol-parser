import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  timeout: 6 * 60 * 1000,
  expect: {
    timeout: 20 * 1000,
  },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.SALEADS_BASE_URL,
    headless: process.env.HEADLESS ? process.env.HEADLESS !== 'false' : true,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 20 * 1000,
    navigationTimeout: 45 * 1000,
  },
});
