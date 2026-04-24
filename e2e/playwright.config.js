const { defineConfig } = require("@playwright/test");

const baseURL = process.env.SALEADS_URL || process.env.BASE_URL;

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 15000,
  },
  fullyParallel: false,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    headless: process.env.HEADED ? false : true,
    viewport: { width: 1600, height: 1000 },
    trace: "on-first-retry",
  },
});
