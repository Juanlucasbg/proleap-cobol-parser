# SaleADS Mi Negocio E2E (Playwright)

This suite validates the complete `saleads_mi_negocio_full_test` workflow:

1. Login with Google
2. Open `Mi Negocio` menu
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Información General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Términos y Condiciones` (same tab or new tab)
9. Validate `Política de Privacidad` (same tab or new tab)
10. Generate final PASS/FAIL report per requested fields

## Environment variables

- `SALEADS_LOGIN_URL` (required): Login page URL for the current SaleADS environment (dev/staging/prod).
- `SALEADS_GOOGLE_ACCOUNT` (optional): Defaults to `juanlucasbarbiergarzon@gmail.com`.

No domain is hardcoded in the test; it runs against whichever login URL you provide.

## Run

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<current-env>/login" npm test
```

## Evidence

The test captures screenshots at key checkpoints and produces `final-report.json` under Playwright `test-results` output for each run.
