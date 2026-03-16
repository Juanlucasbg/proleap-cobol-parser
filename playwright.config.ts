import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 15000,
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    actionTimeout: 15000,
    navigationTimeout: 45000,
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
