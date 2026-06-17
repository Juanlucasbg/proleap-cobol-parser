const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests',
  timeout: 180000,
  expect: {
    timeout: 20000,
  },
  fullyParallel: false,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    headless: process.env.HEADLESS !== 'false',
    viewport: { width: 1536, height: 960 },
    actionTimeout: 30000,
    navigationTimeout: 60000,
    ignoreHTTPSErrors: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    baseURL: process.env.SALEADS_URL,
  },
});
