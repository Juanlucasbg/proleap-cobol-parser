import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 180_000,
  expect: {
    timeout: 20_000
  },
  retries: 0,
  reporter: [
    ["list"],
    ["html", { open: "never" }]
  ],
  use: {
    headless: true,
    viewport: { width: 1440, height: 1024 },
    ignoreHTTPSErrors: true,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure"
  }
});
