const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180_000,
  expect: {
    timeout: 15_000
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: true,
    trace: "retain-on-failure",
    video: "retain-on-failure"
  }
});
