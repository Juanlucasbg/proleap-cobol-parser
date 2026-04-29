import { defineConfig } from "@playwright/test";

const isHeadless = process.env.HEADLESS !== "false";

export default defineConfig({
  testDir: ".",
  testMatch: ["tests/**/*.spec.ts"],
  timeout: 5 * 60 * 1000,
  expect: {
    timeout: 15 * 1000,
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: isHeadless,
    trace: "retain-on-failure",
    video: "retain-on-failure",
    screenshot: "only-on-failure",
    viewport: { width: 1600, height: 1000 },
  },
});
