import { defineConfig } from '@playwright/test';

const baseURL = process.env.SALEADS_URL;

export default defineConfig({
  testDir: './tests',
  timeout: 180_000,
  expect: {
    timeout: 15_000
  },
  fullyParallel: false,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure'
  }
});
