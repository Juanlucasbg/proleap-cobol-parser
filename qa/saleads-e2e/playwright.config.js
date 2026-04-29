const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests',
  timeout: 10 * 60 * 1000,
  fullyParallel: false,
  retries: 0,
  reporter: [
    ['list'],
    ['json', { outputFile: 'artifacts/playwright-report.json' }],
    ['html', { outputFolder: 'artifacts/html-report', open: 'never' }]
  ],
  use: {
    browserName: 'chromium',
    headless: process.env.HEADLESS !== 'false',
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20 * 1000,
    navigationTimeout: 45 * 1000,
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  outputDir: 'artifacts/test-results'
});
