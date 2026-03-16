const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 8 * 60 * 1000,
  expect: {
    timeout: 20 * 1000,
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: process.env.PW_HEADLESS !== "false",
    actionTimeout: 30 * 1000,
    navigationTimeout: 60 * 1000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
