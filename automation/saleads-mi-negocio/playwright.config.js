import { defineConfig } from '@playwright/test';

const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.BASE_URL;

export default defineConfig({
  testDir: './tests',
  timeout: 180_000,
  expect: {
    timeout: 20_000
  },
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    headless: process.env.HEADLESS !== 'false',
    viewport: { width: 1600, height: 1000 },
    baseURL: loginUrl,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure'
  }
});
