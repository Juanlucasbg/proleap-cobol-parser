const { defineConfig } = require("@playwright/test");

const isHeadless = process.env.HEADLESS !== "false";

module.exports = defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  timeout: 180000,
  expect: {
    timeout: 15000,
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: isHeadless,
    viewport: { width: 1440, height: 900 },
    actionTimeout: 15000,
    navigationTimeout: 30000,
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
