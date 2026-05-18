const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 10 * 60 * 1000,
  expect: {
    timeout: 15 * 1000,
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    actionTimeout: 20 * 1000,
    navigationTimeout: 45 * 1000,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
});
