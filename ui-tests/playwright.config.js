// @ts-check
const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 120000,
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: process.env.HEADED === "true" ? false : true,
    viewport: { width: 1440, height: 960 },
    ignoreHTTPSErrors: true,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure"
  }
});
