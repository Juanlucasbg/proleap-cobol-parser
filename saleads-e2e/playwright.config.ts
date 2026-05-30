import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  timeout: 180_000,
  expect: {
    timeout: 15_000,
  },
  fullyParallel: false,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    browserName: 'chromium',
    headless: process.env.HEADLESS !== 'false',
    viewport: { width: 1440, height: 900 },
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
});
