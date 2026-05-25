// @ts-check
const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 8 * 60 * 1000,
  expect: {
    timeout: 15000
  },
  use: {
    headless: true,
    viewport: { width: 1440, height: 960 },
    actionTimeout: 15000,
    navigationTimeout: 30000
  },
  reporter: [["list"], ["html", { open: "never" }]]
});
