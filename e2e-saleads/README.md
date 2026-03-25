# SaleADS Mi Negocio E2E

This folder contains the automated test `saleads_mi_negocio_full_test`.

## Goal

Validate the full **Mi Negocio** workflow after Google login:

1. Login with Google
2. Open Mi Negocio menu
3. Validate Agregar Negocio modal
4. Open Administrar Negocios
5. Validate Información General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Términos y Condiciones (same tab or new tab)
9. Validate Política de Privacidad (same tab or new tab)
10. Produce final PASS/FAIL report by step

## Notes

- The test does **not** hardcode a SaleADS domain.
- It can start from:
  - an already-open SaleADS login page, or
  - `SALEADS_BASE_URL` environment variable.
- Important checkpoints are saved under `screenshots/`.
- The final JSON report is saved under `reports/`.

## Install

```bash
cd e2e-saleads
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
# If SaleADS page is already opened by your runner/session:
npm run test:saleads-mi-negocio

# Or set current environment URL explicitly:
SALEADS_BASE_URL="https://<current-saleads-environment>" npm run test:saleads-mi-negocio
```
