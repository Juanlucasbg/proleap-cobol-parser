# SaleADS Mi Negocio E2E

This folder contains an environment-agnostic Playwright test for the full
`saleads_mi_negocio_full_test` workflow.

## What it validates

The test covers:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal content
4. Administrar Negocios page sections
5. Informacion General section
6. Detalles de la Cuenta section
7. Tus Negocios section
8. Terminos y Condiciones page/tab
9. Politica de Privacidad page/tab
10. Final PASS/FAIL report for each validation block

It captures screenshots at key checkpoints and writes a final JSON report.

## Setup

```bash
cd /workspace/qa/saleads
npm install
npx playwright install chromium
```

## Run

If your runner already opens the SaleADS login page before test execution, run:

```bash
npm run test:saleads-mi-negocio
```

If the browser starts at `about:blank`, provide a login URL dynamically:

```bash
SALEADS_LOGIN_URL="https://<your-environment>/login" npm run test:saleads-mi-negocio
```

To run headed mode:

```bash
npm run test:saleads-mi-negocio:headed
```

## Artifacts

- Screenshots: Playwright test output (`screenshots/*.png`)
- Final report: `artifacts/final-report.json`
