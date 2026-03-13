import { defineConfig } from "@playwright/test";

const baseURL = process.env.SALEADS_URL;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  retries: 0,
  timeout: 180_000,
  expect: {
    timeout: 20_000
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    viewport: { width: 1600, height: 900 }
  }
});
