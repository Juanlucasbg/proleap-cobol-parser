const { defineConfig } = require("@playwright/test");

const headless = process.env.HEADLESS !== "false";

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 240000,
  expect: {
    timeout: 15000,
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    actionTimeout: 30000,
    navigationTimeout: 60000,
  },
});
