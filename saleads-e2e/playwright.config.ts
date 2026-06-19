import { defineConfig, devices } from "@playwright/test";

const isCI = Boolean(process.env.CI);

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  retries: isCI ? 1 : 0,
  timeout: 420_000,
  expect: {
    timeout: 20_000
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.SALEADS_URL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    viewport: { width: 1440, height: 900 }
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] }
    }
  ]
});
