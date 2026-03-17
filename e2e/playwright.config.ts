import { defineConfig } from "@playwright/test";
import dotenv from "dotenv";

dotenv.config();

const baseURL = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;

export default defineConfig({
  testDir: "./tests",
  timeout: 5 * 60 * 1000,
  expect: {
    timeout: 30 * 1000,
  },
  use: {
    baseURL,
    headless: process.env.HEADLESS !== "false",
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20 * 1000,
    navigationTimeout: 45 * 1000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
  },
  reporter: [
    ["list"],
    ["html", { outputFolder: "playwright-report", open: "never" }],
  ],
});
