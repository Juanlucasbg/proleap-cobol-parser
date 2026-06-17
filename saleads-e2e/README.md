# SaleADS E2E automation

This folder contains Playwright end-to-end coverage for the **Mi Negocio** workflow in SaleADS.ai.

## Goals covered

- Login with Google.
- Navigate to `Negocio` > `Mi Negocio`.
- Validate `Agregar Negocio` modal.
- Validate `Administrar Negocios` sections.
- Validate legal links (`Términos y Condiciones`, `Política de Privacidad`) including URL capture.
- Capture screenshots at the requested checkpoints.
- Generate a final JSON report with PASS/FAIL per section.

## Environment-agnostic setup

No domain is hardcoded. Provide the login URL for the target environment through env vars:

- `SALEADS_LOGIN_URL` (preferred)
- `SALEADS_URL` (fallback)
- `BASE_URL` (fallback)

Example:

```bash
cd saleads-e2e
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<your-env>/login" npm run test:mi-negocio
```

## Outputs

On each run, Playwright stores:

- checkpoint screenshots in the test output directory (`checkpoints/`)
- JSON report: `saleads-mi-negocio-final-report.json`
- HTML report (`playwright-report/`)

The final report fields are:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
