import { defineConfig } from "@playwright/test";

const baseURL = process.env.SALEADS_BASE_URL || process.env.SALEADS_LOGIN_URL || undefined;

export default defineConfig({
  testDir: "./tests",
  timeout: 300000,
  expect: {
    timeout: 15000
  },
  reporter: [["list"], ["html", { outputFolder: "playwright-report", open: "never" }]],
  use: {
    baseURL,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
    viewport: { width: 1440, height: 900 }
  }
});
