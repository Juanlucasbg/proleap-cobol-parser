const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 15000
  },
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ["list"],
    ["html", { open: "never" }]
  ],
  use: {
    // Keep this environment-agnostic: URL comes from env vars if provided.
    baseURL: process.env.SALEADS_URL || process.env.BASE_URL,
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure"
  }
});
