import { defineConfig } from "@playwright/test";

const baseURL =
  process.env.SALEADS_START_URL ||
  process.env.SALEADS_LOGIN_URL ||
  process.env.BASE_URL;

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  retries: 0,
  timeout: 180_000,
  expect: {
    timeout: 20_000,
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
