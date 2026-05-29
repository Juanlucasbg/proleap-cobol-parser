# SaleADS Mi Negocio E2E

This folder contains an end-to-end Playwright test that validates the full
"Mi Negocio" workflow requested in `saleads_mi_negocio_full_test`.

## What it validates

1. Login with Google (and account selection when shown).
2. Sidebar > Negocio > Mi Negocio menu expansion.
3. "Agregar Negocio" modal content and controls.
4. "Administrar Negocios" page sections.
5. "Información General".
6. "Detalles de la Cuenta".
7. "Tus Negocios".
8. "Términos y Condiciones" (same tab or new tab).
9. "Política de Privacidad" (same tab or new tab).
10. Final PASS/FAIL report per requested field.

## Environment-agnostic behavior

- No domain is hardcoded.
- Provide the login page URL with one of:
  - `SALEADS_START_URL`
  - `SALEADS_URL`
  - `BASE_URL`
- If none is provided, the test expects to already be on the login page.
  In a fresh Playwright context this is usually `about:blank`, so set one of
  the variables above for reliable runs.

## Run

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
SALEADS_START_URL="https://your-env.example.com/login" npm test
```

## Evidence outputs

- Screenshots: `e2e/artifacts/screenshots/*.png`
- JSON report: `e2e/artifacts/report-*.json`
- Markdown report: `e2e/artifacts/report-*.md`
