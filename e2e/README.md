# SaleADS Mi Negocio E2E

This folder contains the Playwright test `saleads_mi_negocio_full_test`.

## What it validates

The workflow covers:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal fields/buttons
4. Administrar Negocios page sections
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones
9. Politica de Privacidad
10. Final PASS/FAIL report per step

Screenshots are captured for the requested checkpoints and final legal URLs are stored in the attached JSON report.

## Run

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
npm run test:headed
```

## Environment handling

- The test does not hardcode a domain.
- If `SALEADS_BASE_URL` is provided, the test navigates to that URL.
- If not provided, it assumes the browser is already at the login page in the current environment.
- Google account selection attempts to click:
  - `juanlucasbarbiergarzon@gmail.com`

## Useful options

```bash
SALEADS_BASE_URL="https://your-environment-url" npm run test:headed
```
