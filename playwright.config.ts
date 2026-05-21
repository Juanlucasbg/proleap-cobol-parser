import { defineConfig } from "@playwright/test";
import dotenv from "dotenv";

dotenv.config();

const baseURL = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL;

export default defineConfig({
  testDir: "./tests",
  timeout: 180_000,
  expect: {
    timeout: 15_000,
  },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL,
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1600, height: 900 },
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    storageState: process.env.SALEADS_STORAGE_STATE || undefined,
    actionTimeout: 20_000,
    navigationTimeout: 45_000,
  },
});
