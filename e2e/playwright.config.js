const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests',
  timeout: 180000,
  expect: {
    timeout: 15000
  },
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.SALEADS_LOGIN_URL || process.env.BASE_URL || undefined,
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    screenshot: 'off'
  }
});
