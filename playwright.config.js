const { defineConfig } = require("@playwright/test");

const isHeaded = process.env.HEADED === "true";

module.exports = defineConfig({
  testDir: "./tests",
  timeout: 180000,
  fullyParallel: false,
  workers: 1,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: !isHeaded,
    viewport: { width: 1440, height: 900 },
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure"
  }
});
