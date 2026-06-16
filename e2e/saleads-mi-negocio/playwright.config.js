// @ts-check
const { defineConfig } = require("@playwright/test");

const isHeadless = (process.env.SALEADS_HEADLESS || "true").toLowerCase() !== "false";

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 120000,
  retries: 0,
  use: {
    headless: isHeadless,
    viewport: { width: 1440, height: 900 },
    screenshot: "only-on-failure",
    trace: "retain-on-failure"
  },
  reporter: [["list"], ["html", { open: "never" }]]
});
