import { defineConfig } from "@playwright/test";

const timeoutMs = Number(process.env.SALEADS_TEST_TIMEOUT_MS || 180000);

export default defineConfig({
  testDir: "./tests",
  timeout: timeoutMs,
  expect: {
    timeout: 15000
  },
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20000,
    navigationTimeout: 45000,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure"
  }
});
