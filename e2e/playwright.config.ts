import { defineConfig } from "@playwright/test";

const baseURL = process.env.SALEADS_BASE_URL || process.env.BASE_URL;

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  retries: 0,
  timeout: 5 * 60 * 1000,
  use: {
    baseURL,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  reporter: [["list"], ["html", { open: "never" }]],
});
