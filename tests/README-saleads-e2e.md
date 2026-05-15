# SaleADS Mi Negocio E2E

This repository now includes a standalone Playwright test:

- `tests/saleads-mi-negocio-full.spec.ts`

## What it validates

The test automates the workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google
2. Open `Negocio` -> `Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Informacion General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Terminos y Condiciones` (same tab or popup)
9. Validate `Politica de Privacidad` (same tab or popup)
10. Emit PASS/FAIL final report attachment

The test captures screenshots at all key checkpoints.

## Environment-agnostic behavior

- No hardcoded domain is used.
- If the browser page starts at `about:blank`, set one of:
  - `SALEADS_LOGIN_URL`
  - `SALEADS_BASE_URL`

Example:

```bash
SALEADS_LOGIN_URL="https://<current-environment-host>/login" npm run test:e2e:mi-negocio
```

## Setup and run

```bash
npm install
npx playwright install --with-deps chromium
npm run test:e2e:mi-negocio
```
