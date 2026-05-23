import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 6 * 60 * 1000,
  expect: {
    timeout: 20 * 1000,
  },
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  outputDir: "test-results",
  use: {
    headless: true,
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
