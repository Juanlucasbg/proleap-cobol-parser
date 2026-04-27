const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './e2e',
  timeout: 120000,
  expect: {
    timeout: 15000,
  },
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ],
  use: {
    browserName: 'chromium',
    headless: process.env.PW_HEADLESS !== 'false',
    viewport: { width: 1600, height: 900 },
    actionTimeout: 20000,
    navigationTimeout: 45000,
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
});
