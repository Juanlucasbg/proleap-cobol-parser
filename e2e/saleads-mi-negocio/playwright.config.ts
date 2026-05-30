import { defineConfig } from '@playwright/test';

const baseURL = process.env.SALEADS_URL || process.env.BASE_URL;

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  retries: 0,
  timeout: 180_000,
  reporter: [['html', { open: 'never' }], ['list']],
  use: {
    baseURL,
    headless: true,
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20_000,
    navigationTimeout: 45_000,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
});
