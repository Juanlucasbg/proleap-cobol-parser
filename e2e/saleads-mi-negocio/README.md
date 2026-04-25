# SaleADS Mi Negocio E2E

This folder contains an environment-agnostic Playwright workflow test for:

`saleads_mi_negocio_full_test`

The test validates:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal checks
4. Administrar Negocios account page sections
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones (including popup/new-tab handling)
9. Politica de Privacidad (including popup/new-tab handling)
10. Final PASS/FAIL status report generation

## Prerequisites

- Node.js 18+ (or newer LTS)
- Chromium browser installed by Playwright:

```bash
npx playwright install chromium
```

## Install

```bash
npm install
```

## Run

If you already start on the SaleADS login page manually, run:

```bash
npm test
```

If you want the test to open a specific environment login URL first, provide:

```bash
SALEADS_URL="https://your-saleads-environment.example" npm test
```

To run headed:

```bash
npm run test:headed
```

## Evidence and reports

The test writes artifacts under:

- `artifacts/screenshots/` (checkpoint and failure screenshots)
- `artifacts/final-report.json` (PASS/FAIL by requested report fields)
- `artifacts/legal-urls.json` (captured final legal-page URLs)

The Playwright HTML report is produced in:

- `playwright-report/`
