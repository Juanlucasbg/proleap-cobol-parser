import fs from "node:fs";
import path from "node:path";

const reportPath = process.env.SALEADS_REPORT_PATH || path.resolve("artifacts/saleads-mi-negocio-report.json");
const stdoutPath = process.env.SALEADS_REPORT_STDOUT_PATH || "";

function safeReadJson(filePath) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`Report file not found: ${filePath}`);
  }
  const raw = fs.readFileSync(filePath, "utf8");
  return JSON.parse(raw);
}

function fmtStep(stepName, data) {
  const status = data?.status || "SKIPPED";
  const details = Array.isArray(data?.details) ? data.details : [];
  const detailSuffix = details.length ? ` | ${details.join(" | ")}` : "";
  return `- ${stepName}: ${status}${detailSuffix}`;
}

function main() {
  const report = safeReadJson(reportPath);
  const lines = [];

  lines.push("SaleADS Mi Negocio Workflow Report");
  lines.push(`Started at: ${report.startedAt || "n/a"}`);
  lines.push(`Finished at: ${report.finishedAt || "n/a"}`);
  lines.push(`Login URL: ${report.environment?.loginUrl || "n/a"}`);
  lines.push(`Base URL: ${report.environment?.baseURL || "n/a"}`);
  lines.push("");
  lines.push("Validation summary:");
  lines.push(fmtStep("Login", report.steps?.["Login"]));
  lines.push(fmtStep("Mi Negocio menu", report.steps?.["Mi Negocio menu"]));
  lines.push(fmtStep("Agregar Negocio modal", report.steps?.["Agregar Negocio modal"]));
  lines.push(fmtStep("Administrar Negocios view", report.steps?.["Administrar Negocios view"]));
  lines.push(fmtStep("Información General", report.steps?.["Información General"]));
  lines.push(fmtStep("Detalles de la Cuenta", report.steps?.["Detalles de la Cuenta"]));
  lines.push(fmtStep("Tus Negocios", report.steps?.["Tus Negocios"]));
  lines.push(fmtStep("Términos y Condiciones", report.steps?.["Términos y Condiciones"]));
  lines.push(fmtStep("Política de Privacidad", report.steps?.["Política de Privacidad"]));
  lines.push("");
  lines.push("Evidence:");
  const screenshots = report.evidence?.screenshots || [];
  if (screenshots.length === 0) {
    lines.push("- No screenshots captured.");
  } else {
    screenshots.forEach((item) => lines.push(`- ${item}`));
  }
  lines.push(`- Terms URL: ${report.evidence?.terminosUrl || "n/a"}`);
  lines.push(`- Privacy URL: ${report.evidence?.privacidadUrl || "n/a"}`);

  const output = lines.join("\n");

  if (stdoutPath) {
    fs.mkdirSync(path.dirname(stdoutPath), { recursive: true });
    fs.writeFileSync(stdoutPath, output, "utf8");
  }

  process.stdout.write(`${output}\n`);
}

main();
