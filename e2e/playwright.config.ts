import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 180_000,
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1920, height: 1080 },
    actionTimeout: 30_000,
    navigationTimeout: 60_000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure"
  }
});
