const { defineConfig } = require("@playwright/test");
const path = require("path");

module.exports = defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1,
  timeout: 180000,
  expect: {
    timeout: 15000,
  },
  reporter: [
    ["list"],
    ["html", { outputFolder: path.join("artifacts", "html-report"), open: "never" }],
  ],
  use: {
    baseURL: process.env.SALEADS_BASE_URL || undefined,
    headless: process.env.HEADED !== "true",
    viewport: { width: 1600, height: 1000 },
    actionTimeout: 20000,
    navigationTimeout: 45000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
