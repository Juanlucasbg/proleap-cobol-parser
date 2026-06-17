const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 20000
  },
  fullyParallel: false,
  retries: 0,
  use: {
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    ignoreHTTPSErrors: true
  },
  reporter: [
    ["list"],
    ["html", { open: "never" }]
  ]
});
