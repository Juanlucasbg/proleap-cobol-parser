import { defineConfig } from "@playwright/test";
import * as dotenv from "dotenv";

dotenv.config();

const baseURL =
  process.env.SALEADS_LOGIN_URL ||
  process.env.SALEADS_BASE_URL ||
  process.env.BASE_URL;

export default defineConfig({
  testDir: "./tests",
  timeout: 6 * 60 * 1000,
  fullyParallel: false,
  retries: 0,
  reporter: [
    ["list"],
    ["html", { open: "never", outputFolder: "playwright-report" }],
  ],
  use: {
    baseURL,
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 30_000,
    navigationTimeout: 45_000,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
});
