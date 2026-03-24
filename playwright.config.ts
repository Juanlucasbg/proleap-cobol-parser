import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 180_000,
  expect: {
    timeout: 15_000,
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: process.env.HEADED !== "true",
    viewport: { width: 1440, height: 900 },
    ignoreHTTPSErrors: true,
    trace: "on-first-retry",
    video: "retain-on-failure",
  },
});
