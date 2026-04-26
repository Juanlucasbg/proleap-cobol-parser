# SaleADS Mi Negocio E2E

This directory contains an isolated Playwright test that validates the full
`saleads_mi_negocio_full_test` workflow:

1. Login with Google
2. Open `Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Información General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Términos y Condiciones`
9. Validate `Política de Privacidad`
10. Emit PASS/FAIL report for each section

## Environment-agnostic behavior

- The test does **not** hardcode any domain.
- By default it assumes the browser is already on the SaleADS login page.
- Optionally provide `SALEADS_LOGIN_URL` to have the test navigate first.

## Run

```bash
cd e2e/saleads
npm install
npx playwright install chromium
npm test
```

Optional:

```bash
SALEADS_LOGIN_URL="https://your-env-login-url" npm test
HEADLESS=false npm run test:headed
```
