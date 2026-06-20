# SaleADS Mi Negocio full workflow test

This Playwright test automates the complete **Mi Negocio** workflow:

1. Login with Google.
2. Expand **Negocio > Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios** and validate sections.
5. Validate **Información General**, **Detalles de la Cuenta** and **Tus Negocios**.
6. Validate **Términos y Condiciones** and **Política de Privacidad** (same tab or new tab).
7. Generate screenshots and a final JSON report.

## Environment-agnostic behavior

- The test does **not** hardcode any SaleADS URL.
- If you pass `SALEADS_BASE_URL` (or `BASE_URL`), the test navigates there first.
- If no URL is provided, the test starts from the current page context.

## Required / optional environment variables

- `SALEADS_BASE_URL` (optional): login page URL for the target environment.
- `GOOGLE_ACCOUNT_EMAIL` (optional): defaults to `juanlucasbarbiergarzon@gmail.com`.
- `SALEADS_TEST_BUSINESS_NAME` (optional): default name used in the modal.
- `HEADED=true` (optional): run non-headless.

## Run

```bash
npm install
npx playwright install --with-deps chromium
npm run test:saleads-mi-negocio
```

## Artifacts

- Screenshots: `artifacts/screenshots/`
- HTML report: `artifacts/html-report/`
- Final validation report: `artifacts/saleads-mi-negocio-report.json`
