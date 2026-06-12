# SaleADS - Mi Negocio Full Workflow

This automation runs the full SaleADS.ai "Mi Negocio" validation flow using Playwright, including:

- Google login attempt and sidebar validation.
- Mi Negocio menu expansion checks.
- Agregar Negocio modal validation.
- Administrar Negocios page validation.
- Información General / Detalles de la Cuenta / Tus Negocios validation.
- Términos y Condiciones and Política de Privacidad validation (same tab or new tab).
- Evidence screenshots and a final PASS/FAIL report.

## Install

```bash
npm install
```

## Run

```bash
SALEADS_START_URL="https://<current-saleads-environment>/login" npm run test:saleads-mi-negocio
```

> The script does **not** hardcode any domain. Provide the environment URL at runtime with `SALEADS_START_URL`.

## Optional environment variables

- `SALEADS_HEADLESS=false` to run headed.
- `SALEADS_WAIT_TIMEOUT_MS=20000` to increase waits.
- `SALEADS_OUTPUT_DIR=artifacts/saleads-mi-negocio/custom-run` to control output location.
- `SALEADS_DRY_RUN=1` to generate a sample report without browser interaction.

## Output

The script writes:

- `artifacts/saleads-mi-negocio/<timestamp>/*.png` (checkpoint screenshots)
- `artifacts/saleads-mi-negocio/<timestamp>/final-report.json` (step-by-step PASS/FAIL + captured legal URLs)
