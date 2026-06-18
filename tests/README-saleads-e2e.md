# SaleADS Mi Negocio E2E Test

This repository now includes a Playwright test for the full "Mi Negocio" workflow:

- `tests/saleads-mi-negocio-full.spec.js`

## Environment-agnostic execution

The test does **not** hardcode any domain.

Set one of the following environment variables to the current SaleADS login URL:

- `SALEADS_BASE_URL` (preferred)
- `BASE_URL`
- `APP_URL`

Optional:

- `SALEADS_GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)

## Run

```bash
npm run test:e2e:saleads
```

Headed mode:

```bash
npm run test:e2e:headed -- tests/saleads-mi-negocio-full.spec.js
```

## Evidence captured

The test captures screenshots at required checkpoints and writes a final JSON report attachment with PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
