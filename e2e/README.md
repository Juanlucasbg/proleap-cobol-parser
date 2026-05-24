# SaleADS Mi Negocio E2E

This folder contains a cross-environment Playwright test for the workflow:

- Login with Google
- Open `Negocio > Mi Negocio`
- Validate `Agregar Negocio` modal
- Open `Administrar Negocios`
- Validate account sections
- Validate `Términos y Condiciones` and `Política de Privacidad` (same tab or new tab)
- Capture screenshots and a final PASS/FAIL report

## Install

```bash
cd /workspace/e2e
npm install
npx playwright install chromium
```

## Run

You can run in either mode:

1. **Browser already on login page** (no URL is hardcoded, required by prompt)
2. **With explicit start URL**:

```bash
cd /workspace/e2e
SALEADS_URL="https://<current-environment-host>/login" npm run test:saleads:mi-negocio
```

## Output

- Playwright HTML report: `e2e/playwright-report/`
- Screenshots: inside test output artifacts
- Final JSON report: `saleads_mi_negocio_report.json` attached to test results
