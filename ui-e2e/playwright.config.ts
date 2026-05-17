import { defineConfig } from "@playwright/test";

const baseURL = process.env.SALEADS_URL;

export default defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 20000,
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
    actionTimeout: 20000,
    navigationTimeout: 45000,
  },
});
