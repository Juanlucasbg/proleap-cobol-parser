# SaleADS Mi Negocio E2E

This repository now includes a Playwright end-to-end test:

- `tests/saleads_mi_negocio_full_test.spec.js`

## What it validates

The test implements the full workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google
2. Open `Mi Negocio` menu
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Información General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Términos y Condiciones` (same tab or new tab)
9. Validate `Política de Privacidad` (same tab or new tab)
10. Emit final PASS/FAIL report

The test captures screenshots at important checkpoints and writes:

- `evidence/saleads_mi_negocio_full_test/final-report.json`

## Run

Install dependencies and browser:

```bash
npm install
npx playwright install chromium
```

Run against any SaleADS environment (no hardcoded domain):

```bash
SALEADS_LOGIN_URL="https://<your-environment>/login" npx playwright test tests/saleads_mi_negocio_full_test.spec.js
```

If your harness already opens the login page, the test can also use that page directly.
