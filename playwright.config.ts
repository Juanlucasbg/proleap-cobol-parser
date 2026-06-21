import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 15000
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    browserName: "chromium",
    headless: true,
    actionTimeout: 20000,
    navigationTimeout: 30000,
    screenshot: "off",
    trace: "retain-on-failure"
  }
});
