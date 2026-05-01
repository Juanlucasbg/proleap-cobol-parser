import { defineConfig } from "@playwright/test";

const baseURL = process.env.SALEADS_BASE_URL ?? process.env.BASE_URL;

export default defineConfig({
  testDir: "./tests",
  timeout: 6 * 60 * 1000,
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    headless: process.env.PW_HEADLESS === "false" ? false : true,
    viewport: { width: 1600, height: 900 },
    actionTimeout: 20_000,
    navigationTimeout: 45_000,
    screenshot: "only-on-failure",
    trace: "on-first-retry",
    video: "retain-on-failure",
  },
});
