import { defineConfig } from "@playwright/test";

const isHeadless = process.env.HEADLESS !== "false";

export default defineConfig({
  testDir: "./tests",
  timeout: 6 * 60 * 1000,
  fullyParallel: false,
  retries: 0,
  projects: [
    {
      name: "chromium",
      use: { browserName: "chromium" }
    }
  ],
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.SALEADS_URL,
    headless: isHeadless,
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20_000,
    navigationTimeout: 60_000,
    trace: "retain-on-failure",
    video: "retain-on-failure",
    screenshot: "only-on-failure"
  }
});
