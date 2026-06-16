import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e/tests",
  timeout: 180_000,
  expect: {
    timeout: 20_000,
  },
  fullyParallel: false,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: true,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    actionTimeout: 20_000,
    navigationTimeout: 60_000,
  },
});
