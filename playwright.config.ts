import { defineConfig } from "@playwright/test";

const baseURL =
  process.env.SALEADS_URL ||
  process.env.BASE_URL ||
  process.env.PLAYWRIGHT_TEST_BASE_URL;

export default defineConfig({
  testDir: "./tests/e2e",
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    viewport: { width: 1440, height: 900 },
  },
});
