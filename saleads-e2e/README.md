# SaleADS E2E - Mi Negocio workflow

This folder contains an environment-agnostic Playwright test that validates the full `Mi Negocio` flow in SaleADS.ai, including:

1. Google login entry point.
2. Sidebar navigation (`Negocio` -> `Mi Negocio`).
3. `Agregar Negocio` modal checks.
4. `Administrar Negocios` account page sections.
5. Legal links (`Términos y Condiciones` and `Política de Privacidad`) with new-tab/same-tab handling.
6. Evidence screenshots and a final PASS/FAIL report JSON.

## Requirements

- Node.js 20+ (recommended).
- A valid SaleADS environment URL passed at runtime (dev/staging/prod).
- Access to the Google account selector that includes:
  - `juanlucasbarbiergarzon@gmail.com`

## Install

```bash
cd saleads-e2e
npm install
npx playwright install chromium
```

## Run

```bash
cd saleads-e2e
SALEADS_URL="https://<your-current-saleads-environment>" npm test
```

Optional headed run:

```bash
SALEADS_URL="https://<your-current-saleads-environment>" npm run test:headed
```

## Artifacts

Playwright stores artifacts under:

- `saleads-e2e/test-results/`
- `saleads-e2e/playwright-report/`

The test writes a final report file named:

- `saleads-mi-negocio-report.json`

The report includes PASS/FAIL status for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
