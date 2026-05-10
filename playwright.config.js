const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './e2e',
  timeout: 180000,
  expect: {
    timeout: 15000,
  },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    actionTimeout: 15000,
    navigationTimeout: 45000,
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure',
    headless: process.env.HEADLESS !== 'false',
    viewport: { width: 1440, height: 900 },
  },
});
