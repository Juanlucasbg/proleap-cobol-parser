import { defineConfig } from "@playwright/test";

const headless = process.env.HEADLESS !== "false";

export default defineConfig({
  testDir: "./e2e",
  timeout: 10 * 60 * 1000,
  expect: {
    timeout: 20 * 1000
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    actionTimeout: 30 * 1000,
    navigationTimeout: 60 * 1000,
    headless,
    viewport: { width: 1440, height: 900 },
    screenshot: "only-on-failure",
    trace: "retain-on-failure"
  }
});
