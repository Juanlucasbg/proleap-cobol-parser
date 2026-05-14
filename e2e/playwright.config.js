const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 240000,
  expect: {
    timeout: 15000,
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: process.env.HEADLESS !== "false",
    trace: "retain-on-failure",
    video: "retain-on-failure",
    screenshot: "off",
    actionTimeout: 20000,
    navigationTimeout: 45000,
  },
});
