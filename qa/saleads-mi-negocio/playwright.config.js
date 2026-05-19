const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests',
  timeout: 420000,
  expect: {
    timeout: 20000
  },
  fullyParallel: false,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    headless: process.env.HEADLESS !== 'false',
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20000,
    navigationTimeout: 60000,
    ignoreHTTPSErrors: true,
    trace: 'on-first-retry',
    storageState: process.env.SALEADS_STORAGE_STATE || undefined
  }
});
