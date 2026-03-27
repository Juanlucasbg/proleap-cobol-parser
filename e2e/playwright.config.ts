import { defineConfig, devices } from '@playwright/test';

const artifactsDir = process.env.PW_ARTIFACTS_DIR ?? 'artifacts';

export default defineConfig({
  testDir: './tests',
  timeout: 180_000,
  expect: {
    timeout: 15_000,
  },
  fullyParallel: false,
  retries: 0,
  reporter: [
    ['list'],
    ['html', { outputFolder: `${artifactsDir}/playwright-report`, open: 'never' }],
    ['json', { outputFile: `${artifactsDir}/test-results.json` }],
  ],
  use: {
    headless: process.env.HEADED === '1' ? false : true,
    viewport: { width: 1440, height: 900 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 30_000,
    navigationTimeout: 45_000,
  },
  outputDir: `${artifactsDir}/results`,
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
