# SaleADS Mi Negocio full workflow test

This folder contains an end-to-end Playwright test that validates the full "Mi Negocio" workflow in SaleADS.ai, including:

- Login with Google
- Mi Negocio menu expansion
- Agregar Negocio modal validations
- Administrar Negocios view validations
- Información General / Detalles de la Cuenta / Tus Negocios validations
- Términos y Condiciones and Política de Privacidad validations (same tab or popup/new tab)
- Screenshots in key checkpoints
- Final PASS/FAIL report per required field

## Install

```bash
cd /workspace/e2e
npm install
npx playwright install --with-deps
```

## Run

You can run against any environment URL by setting `SALEADS_LOGIN_URL`.  
No domain is hardcoded.

```bash
cd /workspace/e2e
SALEADS_LOGIN_URL="https://your-saleads-login-page" npm run test:saleads-mi-negocio
```

If your execution environment pre-opens the login page, you can omit `SALEADS_LOGIN_URL`.

## Output

- HTML report: `playwright-report/`
- Screenshots/traces/videos: `test-results/`
- Final status report attachment: `final-report.json` (in Playwright test output)
