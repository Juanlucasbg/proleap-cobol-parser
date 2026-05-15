import { defineConfig } from "@playwright/test";

const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;

export default defineConfig({
  testDir: "./tests",
  timeout: 180_000,
  expect: {
    timeout: 20_000
  },
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: loginUrl,
    headless: true,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure"
  }
});
