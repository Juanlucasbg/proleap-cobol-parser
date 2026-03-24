const { defineConfig } = require("@playwright/test");

const isCi = !!process.env.CI;
const baseURL =
  process.env.SALEADS_LOGIN_URL ||
  process.env.saleads_login_url ||
  process.env["saleads.login.url"] ||
  undefined;

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 120000,
  expect: {
    timeout: 15000
  },
  fullyParallel: false,
  retries: isCi ? 1 : 0,
  reporter: [
    ["line"],
    ["html", { outputFolder: "playwright-report", open: "never" }]
  ],
  use: {
    baseURL,
    headless: true,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
    viewport: { width: 1440, height: 900 }
  }
});
