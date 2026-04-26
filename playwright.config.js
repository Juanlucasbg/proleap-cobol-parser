const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './e2e',
  timeout: 15 * 60 * 1000,
  expect: {
    timeout: 20 * 1000,
  },
  workers: 1,
  fullyParallel: false,
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: 'e2e/artifacts/playwright-report' }],
    ['json', { outputFile: 'e2e/artifacts/reports/playwright-results.json' }],
  ],
  use: {
    browserName: 'chromium',
    headless: process.env.HEADLESS !== 'false',
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20 * 1000,
    navigationTimeout: 60 * 1000,
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
});
