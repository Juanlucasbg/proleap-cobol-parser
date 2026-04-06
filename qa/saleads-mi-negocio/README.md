# SaleADS Mi Negocio Full Workflow Test

This folder contains a standalone Playwright test for validating the full
"Mi Negocio" workflow in any SaleADS.ai environment.

## Requirements

- Node.js 20+ (recommended) or 18+
- A reachable SaleADS login page for your target environment
- Valid Google login context available to the browser session

## Setup

```bash
cd qa/saleads-mi-negocio
npm install
npx playwright install chromium
cp .env.example .env
```

Set environment variables in `.env`:

- `SALEADS_BASE_URL`: login page URL for the current environment
- `SALEADS_GOOGLE_ACCOUNT`: Google account to select when selector appears
- `SALEADS_EXPECTED_USER_NAME` (optional): expected user name in Informacion General
- `SALEADS_EXPECTED_USER_EMAIL` (optional): expected user email in Informacion General

## Run

```bash
npm test
```

If `SALEADS_BASE_URL` is omitted, the test assumes the browser is already on the
login page (matching the workflow requirement).

## Outputs

- Screenshots: `artifacts/screenshots/`
- JSON report: `artifacts/reports/mi-negocio-workflow-report.json`

The report returns PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Informacion General
- Detalles de la Cuenta
- Tus Negocios
- Terminos y Condiciones
- Politica de Privacidad
