import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e/tests",
  timeout: 180000,
  expect: {
    timeout: 15000,
  },
  reporter: [["list"], ["html", { outputFolder: "playwright-report", open: "never" }]],
  use: {
    browserName: "chromium",
    headless: true,
    actionTimeout: 15000,
    navigationTimeout: 45000,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
});
