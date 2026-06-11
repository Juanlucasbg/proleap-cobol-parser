const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests',
  timeout: 3 * 60 * 1000,
  expect: {
    timeout: 15 * 1000,
  },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.SALEADS_BASE_URL || process.env.SALEADS_URL || process.env.BASE_URL,
    headless: process.env.HEADLESS !== 'false',
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20 * 1000,
    navigationTimeout: 45 * 1000,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
  },
});
