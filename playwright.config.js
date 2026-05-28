const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./e2e/tests",
  timeout: 180000,
  expect: {
    timeout: 15000,
  },
  fullyParallel: false,
  workers: 1,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: process.env.HEADLESS !== "false",
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
