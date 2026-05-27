import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1,
  timeout: 5 * 60 * 1000,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: process.env.SALEADS_HEADLESS !== "false",
    channel: process.env.SALEADS_BROWSER_CHANNEL || undefined,
    viewport: { width: 1440, height: 900 },
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
    ignoreHTTPSErrors: true,
  },
});
