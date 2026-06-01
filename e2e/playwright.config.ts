import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 5 * 60 * 1000,
  expect: {
    timeout: 15 * 1000,
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: process.env.PW_HEADLESS !== "false",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "off",
  },
});
