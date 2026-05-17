import { defineConfig } from "@playwright/test";

const loginUrl = process.env.SALEADS_LOGIN_URL;

export default defineConfig({
  testDir: "./tests",
  timeout: 180_000,
  expect: {
    timeout: 15_000
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: loginUrl,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure"
  }
});
