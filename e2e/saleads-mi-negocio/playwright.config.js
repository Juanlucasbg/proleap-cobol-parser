const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests',
  timeout: 180000,
  expect: {
    timeout: 15000
  },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: 'playwright-report' }]
  ],
  use: {
    headless: process.env.HEADED ? false : true,
    viewport: { width: 1440, height: 900 },
    actionTimeout: 15000,
    navigationTimeout: 30000,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'retain-on-failure'
  }
});
