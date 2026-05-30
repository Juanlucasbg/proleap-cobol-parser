import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  timeout: 180_000,
  expect: {
    timeout: 15_000
  },
  reporter: [
    ["line"],
    ["json", { outputFile: "artifacts/report/playwright-results.json" }]
  ],
  outputDir: "artifacts/test-output",
  use: {
    headless: process.env.HEADED !== "true",
    viewport: { width: 1600, height: 900 },
    ignoreHTTPSErrors: true,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure"
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] }
    }
  ]
});
