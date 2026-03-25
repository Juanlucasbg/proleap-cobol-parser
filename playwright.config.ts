import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 120_000,
  expect: {
    timeout: 15_000
  },
  fullyParallel: false,
  reporter: [["list"], ["html", { outputFolder: "playwright-report", open: "never" }]],
  use: {
    headless: true,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure"
  }
});
