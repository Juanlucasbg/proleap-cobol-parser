import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 180_000,
  fullyParallel: false,
  retries: 0,
  reporter: [
    ["list"],
    ["html", { open: "never", outputFolder: "playwright-report" }],
    ["json", { outputFile: "reports/results.json" }],
  ],
  use: {
    headless: true,
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
