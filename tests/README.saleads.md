# SaleADS Mi Negocio workflow test

This repository now includes an end-to-end Playwright test named:

- `tests/saleads_mi_negocio_full_test.spec.ts`

## What it validates

The test automates the workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google
2. Expand **Negocio > Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate **Información General**
6. Validate **Detalles de la Cuenta**
7. Validate **Tus Negocios**
8. Validate **Términos y Condiciones** (same tab or new tab)
9. Validate **Política de Privacidad** (same tab or new tab)
10. Emit final PASS/FAIL report JSON

The test captures screenshots at key checkpoints and attaches a machine-readable final report.

## Configuration

Set environment variables at runtime:

- `SALEADS_LOGIN_URL` (required when test starts on a blank page)
- `GOOGLE_ACCOUNT_EMAIL` (optional, default: `juanlucasbarbiergarzon@gmail.com`)
- `PW_HEADLESS` (optional, set `false` to run headed)

## Run

```bash
npx playwright install chromium
npm run test:saleads:mi-negocio
```

Artifacts are written under Playwright output directories (`playwright-report/`, `test-results/`).
