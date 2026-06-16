import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 180000,
  expect: {
    timeout: 15000
  },
  use: {
    headless: true,
    screenshot: "only-on-failure",
    trace: "retain-on-failure"
  },
  reporter: [
    ["list"],
    ["html", { open: "never" }]
  ]
});
