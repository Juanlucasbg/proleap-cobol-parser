import { defineConfig } from "@playwright/test";

const loginUrl = process.env.SALEADS_LOGIN_URL;

export default defineConfig({
  testDir: "./tests",
  timeout: 180_000,
  expect: {
    timeout: 15_000,
  },
  use: {
    baseURL: loginUrl,
    trace: "on-first-retry",
    video: "retain-on-failure",
    screenshot: "only-on-failure",
    actionTimeout: 20_000,
    navigationTimeout: 30_000,
  },
  reporter: [
    ["list"],
    ["html", { open: "never" }],
  ],
});
