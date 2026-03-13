import { defineConfig } from "@playwright/test";

const baseURL =
  process.env.SALEADS_LOGIN_URL ||
  process.env.SALEADS_URL ||
  process.env.BASE_URL ||
  undefined;

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: [["list"], ["html", { open: "never" }]],
  timeout: 180_000,
  expect: {
    timeout: 15_000,
  },
  use: {
    baseURL,
    headless: process.env.HEADED ? false : true,
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    trace: "retain-on-failure",
    viewport: { width: 1600, height: 1000 },
    actionTimeout: 20_000,
    navigationTimeout: 30_000,
    locale: "es-ES",
  },
});
