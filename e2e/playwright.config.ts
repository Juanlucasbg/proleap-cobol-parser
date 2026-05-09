import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 180_000,
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: true,
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20_000,
    navigationTimeout: 45_000,
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
