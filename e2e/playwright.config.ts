import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 15000,
  },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20000,
    navigationTimeout: 45000,
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
