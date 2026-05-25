import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  timeout: 4 * 60 * 1000,
  expect: {
    timeout: 20 * 1000,
  },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    headless: process.env.PW_HEADLESS !== 'false',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20 * 1000,
    navigationTimeout: 60 * 1000,
  },
});
