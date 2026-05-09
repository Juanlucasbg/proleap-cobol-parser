const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./e2e",
  timeout: 3 * 60 * 1000,
  expect: {
    timeout: 20 * 1000,
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: true,
    ignoreHTTPSErrors: true,
    actionTimeout: 20 * 1000,
    navigationTimeout: 30 * 1000,
  },
});
