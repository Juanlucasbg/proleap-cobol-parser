// @ts-check
const { defineConfig } = require("@playwright/test");

const headlessEnv = (process.env.PW_HEADLESS || "true").toLowerCase();
const runHeadless = headlessEnv !== "false" && headlessEnv !== "0";

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 5 * 60 * 1000,
  expect: {
    timeout: 20 * 1000,
  },
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: runHeadless,
    viewport: { width: 1440, height: 900 },
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
    actionTimeout: 20 * 1000,
    navigationTimeout: 45 * 1000,
  },
});
