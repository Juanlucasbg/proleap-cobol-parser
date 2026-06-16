# SaleADS E2E checks

This folder contains an environment-agnostic Playwright test for the complete **Mi Negocio** workflow.

## Covered flow

- Login with Google
- Open **Mi Negocio** menu and validate submenu items
- Validate **Agregar Negocio** modal contents
- Open **Administrar Negocios** and validate:
  - Informacion General
  - Detalles de la Cuenta
  - Tus Negocios
- Validate legal links:
  - Terminos y Condiciones
  - Politica de Privacidad
- Generate a final JSON PASS/FAIL report by section

## Requirements

- Node.js 18+
- Playwright browsers installed
- A reachable login page URL for the target SaleADS environment

## Install

```bash
npm install --prefix e2e
npx --prefix e2e playwright install
```

## Run

```bash
SALEADS_START_URL="https://<your-saleads-environment>/login" npm --prefix e2e run test:saleads-mi-negocio
```

Optional:

- `HEADLESS=false` to run headed
- `SALEADS_TEST_TIMEOUT_MS=240000` to increase timeout

## Evidence output

- Checkpoint screenshots are stored by Playwright in test outputs
- Final report file:
  - `e2e/reports/saleads-mi-negocio-last-report.json`

The report includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Informacion General
- Detalles de la Cuenta
- Tus Negocios
- Terminos y Condiciones (including final URL)
- Politica de Privacidad (including final URL)
