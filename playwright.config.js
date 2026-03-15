const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests/e2e",
  timeout: 3 * 60 * 1000,
  expect: {
    timeout: 20 * 1000,
  },
  reporter: [
    ["list"],
    ["html", { open: "never" }],
  ],
  use: {
    headless: !process.env.HEADED,
    actionTimeout: 20 * 1000,
    navigationTimeout: 45 * 1000,
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
