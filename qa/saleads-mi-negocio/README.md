# SaleADS Mi Negocio full workflow test

This package contains an end-to-end Playwright test that validates the full "Mi Negocio" workflow requested in automation `saleads_mi_negocio_full_test`.

## What it validates

The test executes and reports PASS/FAIL for:

1. Login
2. Mi Negocio menu
3. Agregar Negocio modal
4. Administrar Negocios view
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones
9. Politica de Privacidad

It also captures screenshots at key checkpoints and stores legal-page URLs in a final JSON attachment (`final-report.json`).

## Environment-agnostic usage

No domain is hardcoded.

- If `SALEADS_LOGIN_URL` (or `BASE_URL`) is provided, the test navigates there.
- If not provided, the test expects the browser/page to already be on the SaleADS login page.

## Run locally

```bash
cd qa/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<your-environment-login>" npm run test:headed
```

Optional environment variables:

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS` (`false` to run headed)
- `SALEADS_CLICK_SETTLE_MS` (default: `1500`)

## Artifacts

- HTML report: `playwright-report/`
- Screenshots and report attachment: `test-results/`
