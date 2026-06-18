import { defineConfig } from "@playwright/test";

const configuredBaseUrl =
  process.env.SALEADS_BASE_URL ||
  process.env.BASE_URL ||
  process.env.APP_URL ||
  undefined;

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  retries: 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: configuredBaseUrl,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    viewport: { width: 1440, height: 900 },
  },
});
