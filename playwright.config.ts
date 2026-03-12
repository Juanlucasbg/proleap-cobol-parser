import { defineConfig } from "@playwright/test";

const baseURL = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
const headless = process.env.HEADLESS === "false" ? false : true;

export default defineConfig({
  testDir: "./tests",
  timeout: 5 * 60 * 1000,
  expect: {
    timeout: 20 * 1000,
  },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["line"], ["html", { open: "never" }]],
  use: {
    baseURL,
    headless,
    viewport: { width: 1440, height: 900 },
    actionTimeout: 30 * 1000,
    navigationTimeout: 60 * 1000,
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    trace: "on-first-retry",
  },
});
