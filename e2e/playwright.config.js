const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  timeout: 180000,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL || undefined,
    headless: process.env.HEADED ? false : true,
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20000,
    navigationTimeout: 45000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure'
  }
});
