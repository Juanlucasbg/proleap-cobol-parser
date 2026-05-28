# SaleADS.ai E2E tests

## Test included

- `tests/saleads_mi_negocio_full_test.spec.ts`

This test automates the complete Mi Negocio workflow:

1. Login with Google.
2. Open Mi Negocio menu.
3. Validate Agregar Negocio modal.
4. Open Administrar Negocios.
5. Validate Información General.
6. Validate Detalles de la Cuenta.
7. Validate Tus Negocios.
8. Validate Términos y Condiciones.
9. Validate Política de Privacidad.
10. Generate a final PASS/FAIL report.

## Environment variables

- `SALEADS_LOGIN_URL` (required when the test opens a new browser context).
- `SALEADS_GOOGLE_ACCOUNT` (optional, defaults to `juanlucasbarbiergarzon@gmail.com`).
- `HEADLESS` (optional, set `false` to run headed mode).

## Run

```bash
npm install
npx playwright install --with-deps chromium
npm run test:mi-negocio
```

Screenshots and report artifacts are stored in Playwright output directories (`test-results` and `playwright-report`).
