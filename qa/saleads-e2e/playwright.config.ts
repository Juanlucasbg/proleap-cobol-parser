import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  outputDir: "./test-results",
  fullyParallel: false,
  retries: 0,
  timeout: 3 * 60 * 1000,
  expect: {
    timeout: 15 * 1000,
  },
  reporter: [
    ["list"],
    ["html", { open: "never" }],
  ],
  use: {
    headless: process.env.PW_HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    ignoreHTTPSErrors: true,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
