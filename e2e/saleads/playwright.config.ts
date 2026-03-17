import { defineConfig } from "@playwright/test";

const baseURL = process.env.SALEADS_URL || process.env.BASE_URL;

export default defineConfig({
  testDir: "./tests",
  timeout: 120000,
  expect: {
    timeout: 15000,
  },
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    headless: process.env.HEADLESS === "false" ? false : true,
    viewport: { width: 1440, height: 900 },
    ignoreHTTPSErrors: true,
    trace: "on-first-retry",
    screenshot: "off",
    video: "off",
    actionTimeout: 20000,
    navigationTimeout: 45000,
  },
});
