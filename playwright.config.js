const { defineConfig } = require('@playwright/test');
require('dotenv').config();

module.exports = defineConfig({
  testDir: './tests/e2e',
  timeout: 180000,
  expect: {
    timeout: 10000,
  },
  fullyParallel: false,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    headless: process.env.PW_HEADLESS !== 'false',
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20000,
    navigationTimeout: 45000,
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { browserName: 'chromium' },
    },
  ],
});
