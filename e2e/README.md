# SaleADS.ai Mi Negocio E2E

This folder contains a Playwright end-to-end test for the full **Mi Negocio** workflow:

- Login with Google
- Sidebar > Negocio > Mi Negocio expansion
- "Agregar Negocio" modal validation
- "Administrar Negocios" account page validation
- Información General, Detalles de la Cuenta, and Tus Negocios checks
- Términos y Condiciones + Política de Privacidad validation (same tab or new tab)
- Checkpoint screenshots and final PASS/FAIL report

## Requirements

- Node.js 18+
- Playwright browser binaries

Install dependencies:

```bash
cd /workspace/e2e
npm install
npx playwright install chromium
```

## Run

Provide the login page URL for the active environment (dev/staging/prod) through an environment variable.
No domain is hardcoded in the test.

```bash
cd /workspace/e2e
SALEADS_LOGIN_URL="https://<current-environment>/login" npm test
```

Optional variables:

- `SALEADS_EXPECTED_NAME_TOKEN`: token used to validate user name visibility (default: `juan`)
- `HEADLESS=false`: run headed mode from the same `npm test` command

## Output evidence

Playwright stores evidence in `playwright-report/` and `test-results/`:

- Step checkpoint screenshots
- Failure screenshots
- Trace/video on failure
- `final-report.json` attachment with PASS/FAIL statuses and legal URLs
