const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 15000,
  },
  fullyParallel: false,
  retries: 0,
  reporter: [["line"], ["html", { open: "never" }]],
  use: {
    headless: true,
    actionTimeout: 15000,
    navigationTimeout: 30000,
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
