import { defineConfig } from "@playwright/test";

const isHeadless = process.env.HEADLESS !== "false";

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1,
  timeout: 15 * 60 * 1000,
  expect: {
    timeout: 30 * 1000,
  },
  reporter: [
    ["list"],
    ["html", { open: "never" }],
  ],
  use: {
    headless: isHeadless,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    viewport: { width: 1600, height: 1000 },
  },
  outputDir: "test-results",
});
