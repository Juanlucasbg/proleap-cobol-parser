# SaleADS Mi Negocio full workflow test

This repository now includes a Playwright end-to-end test for:

- Login with Google (continuing after login)
- Mi Negocio menu expansion
- Agregar Negocio modal validations
- Administrar Negocios view and account sections
- Legal links validation (including new-tab handling)
- Final PASS/FAIL report generation

## Install

```bash
npm install
npx playwright install chromium
```

## Run

Set the login page URL for your target environment (dev/staging/prod):

```bash
export SALEADS_BASE_URL="https://<your-saleads-login-page>"
npm run test:saleads-mi-negocio
```

Optional:

- `GOOGLE_ACCOUNT_EMAIL` is fixed in this workflow to `juanlucasbarbiergarzon@gmail.com` (as requested)
- `HEADLESS` is controlled in `playwright.config.js` (default is `true`)

Example:

```bash
export SALEADS_BASE_URL="https://<your-saleads-login-page>"
npm run test:saleads-mi-negocio
```

## Outputs

- Checkpoint screenshots: `artifacts/saleads-mi-negocio/`
- Step report: `artifacts/saleads-mi-negocio/report.json`
- Playwright JSON report: `test-results/playwright-report.json`
- Playwright HTML report (when generated): `playwright-report/`
