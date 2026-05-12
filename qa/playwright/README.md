# SaleADS - Mi Negocio Full Workflow Test

This folder contains a standalone Playwright test for the full workflow:

- Login with Google
- Open **Mi Negocio** menu
- Validate **Agregar Negocio** modal
- Open **Administrar Negocios**
- Validate account sections
- Validate **Terminos y Condiciones**
- Validate **Politica de Privacidad**
- Produce a final PASS/FAIL report as an attached JSON artifact

## Test file

- `tests/saleads-mi-negocio-full.spec.js`

## Environment-agnostic behavior

The test does **not** hardcode a SaleADS domain.

- If the browser session is already on the login page, the test continues from there.
- If Playwright starts on `about:blank`, you can provide one of:
  - `SALEADS_LOGIN_URL`
  - `SALEADS_URL`
  - `BASE_URL`

## Run locally

```bash
cd qa/playwright
npm init -y
npm install -D @playwright/test
npx playwright install
npx playwright test tests/saleads-mi-negocio-full.spec.js
```

Optional URL injection:

```bash
SALEADS_LOGIN_URL="https://your-saleads-environment/login" \
npx playwright test tests/saleads-mi-negocio-full.spec.js
```

## Evidence and checkpoints

The test captures screenshots at key checkpoints:

- `01-dashboard-loaded.png`
- `02-mi-negocio-menu-expanded.png`
- `03-agregar-negocio-modal.png`
- `04-administrar-negocios-page.png`
- `08-terminos-y-condiciones.png`
- `09-politica-de-privacidad.png`

It also stores a final JSON artifact (`final-report.json`) with PASS/FAIL status per validation block and collected legal-page URLs.
