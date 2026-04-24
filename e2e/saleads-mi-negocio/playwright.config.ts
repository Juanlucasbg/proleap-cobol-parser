import { defineConfig } from "@playwright/test";

const baseURL = process.env.SALEADS_URL ?? process.env.SALEADS_BASE_URL;

export default defineConfig({
  testDir: "./tests",
  timeout: 240_000,
  expect: {
    timeout: 20_000,
  },
  fullyParallel: false,
  reporter: [["list"], ["html", { open: "never" }]],
  outputDir: "test-results",
  use: {
    baseURL,
    headless: process.env.HEADED !== "true",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
});
