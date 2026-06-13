import { defineConfig } from "@playwright/test";

const headless = process.env.HEADLESS !== "false";
const slowMo = Number(process.env.PW_SLOWMO ?? 150);

export default defineConfig({
  testDir: "./tests",
  timeout: 4 * 60 * 1000,
  expect: {
    timeout: 20 * 1000,
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    browserName: "chromium",
    headless,
    actionTimeout: 20 * 1000,
    navigationTimeout: 40 * 1000,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    viewport: { width: 1440, height: 900 },
    launchOptions: {
      slowMo,
    },
  },
});
