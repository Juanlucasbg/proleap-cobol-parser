import { defineConfig, devices } from "@playwright/test";
import * as path from "path";
import * as dotenv from "dotenv";

dotenv.config({ path: path.resolve(__dirname, ".env") });

const baseURL = process.env.SALEADS_BASE_URL;

export default defineConfig({
  testDir: "./tests",
  timeout: 120000,
  expect: {
    timeout: 15000,
  },
  fullyParallel: false,
  retries: 0,
  reporter: [["html", { open: "never" }], ["list"]],
  use: {
    baseURL,
    actionTimeout: 20000,
    navigationTimeout: 45000,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    viewport: { width: 1440, height: 900 },
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
