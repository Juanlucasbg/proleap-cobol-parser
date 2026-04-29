const fs = require("fs");

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
];

function createStepReporter() {
  const results = Object.fromEntries(
    REPORT_FIELDS.map((name) => [name, { status: "FAIL", details: "Not executed." }])
  );

  const legalUrls = {
    terminosYCondiciones: "N/A",
    politicaDePrivacidad: "N/A"
  };

  function pass(step, details) {
    if (!results[step]) return;
    results[step] = { status: "PASS", details: details || "Validation passed." };
  }

  function fail(step, details) {
    if (!results[step]) return;
    results[step] = { status: "FAIL", details: details || "Validation failed." };
  }

  function setLegalUrl(kind, value) {
    if (kind === "terminosYCondiciones" || kind === "politicaDePrivacidad") {
      legalUrls[kind] = value || "N/A";
    }
  }

  function getResults() {
    return REPORT_FIELDS.map((name) => ({ step: name, ...results[name] }));
  }

  function renderSummary() {
    const lines = ["Final Report - saleads_mi_negocio_full_test", ""];
    for (const entry of getResults()) {
      lines.push(`- ${entry.step}: ${entry.status}`);
      lines.push(`  Details: ${entry.details}`);
    }
    lines.push("");
    lines.push(`- Términos y Condiciones URL: ${legalUrls.terminosYCondiciones}`);
    lines.push(`- Política de Privacidad URL: ${legalUrls.politicaDePrivacidad}`);
    return lines.join("\n");
  }

  function writeJsonReport(outputPath) {
    const report = {
      test: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      results: getResults(),
      legalUrls
    };
    fs.writeFileSync(outputPath, JSON.stringify(report, null, 2), "utf8");
    return report;
  }

  return {
    pass,
    fail,
    setLegalUrl,
    getResults,
    renderSummary,
    writeJsonReport
  };
}

module.exports = { createStepReporter, REPORT_FIELDS };
