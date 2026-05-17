const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 240_000,
  expect: {
    timeout: 20_000,
  },
  reporter: [
    ["list"],
    ["html", { open: "never", outputFolder: "playwright-report" }],
  ],
  use: {
    headless: process.env.HEADLESS !== "false",
    trace: "retain-on-failure",
    viewport: { width: 1600, height: 900 },
    actionTimeout: 20_000,
    navigationTimeout: 45_000,
  },
});
