import { defineConfig } from "@playwright/test";

const isCI = !!process.env.CI;

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  retries: isCI ? 1 : 0,
  timeout: 4 * 60 * 1000,
  reporter: [
    ["list"],
    ["html", { open: "never" }]
  ],
  use: {
    headless: process.env.HEADED !== "true",
    viewport: { width: 1440, height: 900 },
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
    actionTimeout: 20_000,
    navigationTimeout: 45_000
  }
});
