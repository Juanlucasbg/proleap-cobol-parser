import fs from "node:fs";
import path from "node:path";

const reportPath = path.join(process.cwd(), "tests", "e2e", "artifacts", "mi-negocio-latest-report.json");

if (!fs.existsSync(reportPath)) {
  console.error(`Report not found: ${reportPath}`);
  process.exit(1);
}

const report = JSON.parse(fs.readFileSync(reportPath, "utf-8"));

console.log("saleads_mi_negocio_full_test");
console.log(`Generated at: ${report.generatedAt}`);
console.log(`Environment: ${report.environment}`);
console.log(`Overall status: ${report.overallStatus}`);
console.log("");

for (const [stepName, result] of Object.entries(report.steps)) {
  console.log(`${stepName}: ${result.status} - ${result.details}`);
}

if (report.finalUrls && Object.keys(report.finalUrls).length > 0) {
  console.log("");
  console.log("Final legal URLs:");
  for (const [key, value] of Object.entries(report.finalUrls)) {
    console.log(`- ${key}: ${value}`);
  }
}

if (report.screenshots?.length) {
  console.log("");
  console.log("Screenshots:");
  for (const screenshotPath of report.screenshots) {
    console.log(`- ${screenshotPath}`);
  }
}
