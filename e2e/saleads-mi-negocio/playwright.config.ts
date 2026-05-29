import { defineConfig } from "@playwright/test";

const startUrl = process.env.SALEADS_START_URL;

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  retries: 0,
  timeout: 180_000,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    browserName: "chromium",
    headless: process.env.HEADLESS !== "false",
    actionTimeout: 30_000,
    navigationTimeout: 60_000,
    baseURL: startUrl,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure"
  }
});
