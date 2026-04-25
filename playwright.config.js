const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests',
  timeout: 180000,
  expect: {
    timeout: 20000
  },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    headless: true,
    viewport: { width: 1440, height: 900 },
    trace: 'on-first-retry',
    screenshot: 'off',
    video: 'off'
  }
});
