import { defineConfig } from "@playwright/test";

const ci = process.env.CI === "true";

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  timeout: 180_000,
  expect: {
    timeout: 20_000,
  },
  retries: ci ? 1 : 0,
  reporter: [
    ["list"],
    ["html", { open: "never", outputFolder: "playwright-report" }],
    ["json", { outputFile: "test-results/test-results.json" }],
  ],
  use: {
    browserName: "chromium",
    headless: ci,
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20_000,
    navigationTimeout: 45_000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
