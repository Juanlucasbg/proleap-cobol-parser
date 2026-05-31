# SaleADS Mi Negocio Full Workflow Test

This folder contains an environment-agnostic Playwright runner for validating the complete SaleADS "Mi Negocio" workflow, including:

- Login with Google
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios view and section checks
- Información General / Detalles de la Cuenta / Tus Negocios checks
- Legal links (Términos y Condiciones / Política de Privacidad), including URL capture
- Checkpoint screenshots and final PASS/FAIL report

## Requirements

- Node.js 18+
- `npm install` already executed in this folder

## Configuration

The script does **not** hardcode a SaleADS URL.

Use one of these modes:

1. Launch a fresh browser and navigate via URL:
   - `SALEADS_START_URL=https://<current-env-login-page>`
2. Connect to an existing browser session already on the login page:
   - `PW_CONNECT_WS_ENDPOINT=<ws endpoint>` or `PW_CDP_ENDPOINT=<cdp endpoint>`

Optional:

- `HEADLESS=false` to run headed mode
- `SALEADS_EVIDENCE_DIR=/absolute/path/to/output`

## Run

```bash
npm run test:workflow
```

## Output

Evidence is written to:

```text
qa/saleads-mi-negocio/artifacts/saleads-mi-negocio-<timestamp>/
```

Artifacts include:

- step screenshots
- `saleads-mi-negocio-report.json`
- `saleads-mi-negocio-report.md`
