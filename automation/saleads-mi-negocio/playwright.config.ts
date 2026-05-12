import { defineConfig } from "@playwright/test";

const baseURL =
  process.env.SALEADS_LOGIN_URL ||
  process.env.SALEADS_BASE_URL ||
  process.env.BASE_URL;

export default defineConfig({
  testDir: "./tests",
  timeout: 240000,
  expect: {
    timeout: 15000
  },
  fullyParallel: false,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    headless: true,
    trace: "on-first-retry",
    viewport: { width: 1440, height: 900 }
  }
});
