import "dotenv/config";
import { defineConfig } from "@playwright/test";
import * as path from "path";

const outputRoot = path.resolve(__dirname, "artifacts");

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  retries: 0,
  timeout: 180_000,
  expect: {
    timeout: 20_000,
  },
  reporter: [
    ["list"],
    ["html", { outputFolder: "playwright-report", open: "never" }],
    ["junit", { outputFile: path.join(outputRoot, "reports", "junit.xml") }],
  ],
  use: {
    headless: true,
    viewport: { width: 1600, height: 1000 },
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    actionTimeout: 25_000,
    navigationTimeout: 45_000,
  },
});
