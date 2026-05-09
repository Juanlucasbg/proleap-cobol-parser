const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests',
  timeout: 180000,
  expect: {
    timeout: 30000
  },
  retries: 0,
  reporter: [
    ['list'],
    ['json', { outputFile: 'artifacts/playwright-run-report.json' }]
  ],
  use: {
    browserName: 'chromium',
    headless: true,
    actionTimeout: 30000,
    navigationTimeout: 60000,
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    trace: 'retain-on-failure'
  },
  outputDir: 'artifacts/test-results'
});
