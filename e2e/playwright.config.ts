import { defineConfig } from "@playwright/test";

const defaultTimeoutMs = Number(process.env.PW_TIMEOUT_MS ?? 30_000);

export default defineConfig({
  testDir: "./tests",
  timeout: defaultTimeoutMs,
  expect: {
    timeout: 10_000
  },
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.BASE_URL,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
    actionTimeout: 10_000
  }
});
