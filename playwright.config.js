const { defineConfig } = require("@playwright/test");

const headless = process.env.HEADLESS !== "false";

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 6 * 60 * 1000,
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    browserName: "chromium",
    headless,
    viewport: { width: 1440, height: 900 },
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    actionTimeout: 20 * 1000,
    navigationTimeout: 30 * 1000,
  },
});
