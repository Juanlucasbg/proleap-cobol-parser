import { defineConfig } from "@playwright/test";

const isCi = !!process.env.CI;

export default defineConfig({
  testDir: "./tests/e2e",
  timeout: 300_000,
  expect: {
    timeout: 30_000
  },
  fullyParallel: false,
  retries: isCi ? 1 : 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: process.env.HEADLESS === "false" ? false : true,
    actionTimeout: 30_000,
    navigationTimeout: 45_000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure"
  }
});
