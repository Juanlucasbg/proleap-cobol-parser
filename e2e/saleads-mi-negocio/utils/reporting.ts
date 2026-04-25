import fs from "node:fs";
import path from "node:path";
import type { Page, TestInfo } from "@playwright/test";

export type ValidationStatus = "PASS" | "FAIL";

export interface StepResult {
  name: string;
  status: ValidationStatus;
  details: string;
}

const EVIDENCE_DIR = "evidence";

function sanitizeFileName(input: string): string {
  return input
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

export async function checkpointScreenshot(
  page: Page,
  testInfo: TestInfo,
  label: string,
  options?: { fullPage?: boolean }
): Promise<string> {
  const fileName = `${sanitizeFileName(label)}.png`;
  const relativePath = path.join(EVIDENCE_DIR, fileName);
  const destination = testInfo.outputPath(relativePath);

  await page.screenshot({
    path: destination,
    fullPage: options?.fullPage ?? false,
  });

  await testInfo.attach(label, {
    path: destination,
    contentType: "image/png",
  });

  return relativePath;
}

export async function writeJsonEvidence(
  testInfo: TestInfo,
  fileName: string,
  payload: unknown
): Promise<string> {
  const relativePath = path.join(EVIDENCE_DIR, fileName);
  const destination = testInfo.outputPath(relativePath);
  fs.mkdirSync(path.dirname(destination), { recursive: true });
  fs.writeFileSync(destination, JSON.stringify(payload, null, 2), "utf8");

  await testInfo.attach(fileName, {
    path: destination,
    contentType: "application/json",
  });

  return relativePath;
}
