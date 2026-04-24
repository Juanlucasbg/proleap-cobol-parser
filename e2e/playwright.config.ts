import { defineConfig } from '@playwright/test';

const artifactsDir = process.env.PW_ARTIFACTS_DIR ?? 'artifacts';

export default defineConfig({
  testDir: './tests',
  timeout: 180_000,
  fullyParallel: false,
  retries: 0,
  reporter: [
    ['list'],
    ['html', { open: 'never', outputFolder: `${artifactsDir}/playwright-report` }],
    ['json', { outputFile: `${artifactsDir}/reports/results.json` }],
  ],
  use: {
    headless: process.env.HEADED === '1' ? false : true,
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
});
