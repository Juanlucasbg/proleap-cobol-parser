# SaleADS Mi Negocio full workflow E2E

This folder contains an environment-agnostic Playwright test for the `saleads_mi_negocio_full_test` workflow:

1. Login with Google.
2. Open `Negocio -> Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate account sections and legal links.
6. Generate a final PASS/FAIL report per requested field.

## Why environment-agnostic

The test does not hardcode any SaleADS domain. Use an environment variable to point to the current environment:

- Dev: `SALEADS_BASE_URL=https://<dev-host>`
- Staging: `SALEADS_BASE_URL=https://<staging-host>`
- Production: `SALEADS_BASE_URL=https://<prod-host>`

If `SALEADS_BASE_URL` is not provided, the test expects the browser session to already be on the SaleADS login page.

## Run locally

```bash
cd qa/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
SALEADS_BASE_URL="https://your-saleads-environment" npm test
```

For headed mode:

```bash
HEADLESS=false SALEADS_BASE_URL="https://your-saleads-environment" npm run test:headed
```

## Output artifacts

- Screenshots at important checkpoints (dashboard, menu, modal, account page, legal pages)
- Final JSON report with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad

The final report is attached as `final-report.json` in Playwright test output.
