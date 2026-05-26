import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests/e2e",
  timeout: 180_000,
  expect: {
    timeout: 15_000,
  },
  fullyParallel: false,
  reporter: [
    ["list"],
    ["html", { open: "never", outputFolder: "playwright-report" }],
  ],
  use: {
    browserName: "chromium",
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    ignoreHTTPSErrors: true,
    trace: "retain-on-failure",
    screenshot: "off",
    video: "off",
  },
});
