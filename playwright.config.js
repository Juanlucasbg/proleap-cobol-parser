const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './e2e',
  timeout: 10 * 60 * 1000,
  expect: {
    timeout: 15 * 1000
  },
  fullyParallel: false,
  reporter: [
    ['list'],
    ['html', { open: 'never' }]
  ],
  use: {
    actionTimeout: 30 * 1000,
    navigationTimeout: 60 * 1000,
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    headless: process.env.HEADLESS !== 'false'
  }
});
