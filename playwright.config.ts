import { defineConfig } from "@playwright/test";

const startUrl = process.env.SALEADS_START_URL ?? process.env.SALEADS_URL;

export default defineConfig({
  testDir: "./e2e",
  timeout: 180000,
  expect: {
    timeout: 15000,
  },
  reporter: [
    ["list"],
    ["html", { outputFolder: "playwright-report", open: "never" }],
  ],
  use: {
    baseURL: startUrl,
    headless: true,
    viewport: { width: 1440, height: 900 },
    trace: "retain-on-failure",
    screenshot: "off",
  },
});
