# SaleADS Mi Negocio full workflow test

This Playwright suite automates the `saleads_mi_negocio_full_test` flow:

- Login with Google
- Open and validate the **Mi Negocio** menu
- Validate **Agregar Negocio** modal
- Open and validate **Administrar Negocios** sections
- Validate **Términos y Condiciones** and **Política de Privacidad** links (same tab or new tab)
- Capture checkpoint screenshots
- Produce a final PASS/FAIL report per required fields

## Environment-agnostic behavior

The test never hardcodes a domain. Provide the login page URL for the active environment:

- `SALEADS_LOGIN_URL`, or
- `BASE_URL`

## Quick start

```bash
cd qa/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<current-env-login-url>" npm run test:saleads-mi-negocio
```

Optional:

- `GOOGLE_ACCOUNT_EMAIL` (defaults to `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS=false` to watch the browser

## Evidence and report outputs

- Screenshots, trace, and video: `test-results/`
- HTML report: `playwright-report/`
- Final JSON report: `artifacts/saleads-mi-negocio-final-report.json`
