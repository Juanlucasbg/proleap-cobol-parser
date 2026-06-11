# SaleADS Mi Negocio E2E

This folder contains an end-to-end Playwright test named:

- `saleads_mi_negocio_full_test`

## What it validates

The workflow covers:

1. Google login (including account picker for `juanlucasbarbiergarzon@gmail.com` when shown)
2. Sidebar > `Negocio` > `Mi Negocio` expansion
3. `Agregar Negocio` modal validations
4. `Administrar Negocios` page and section validations
5. `Información General` validations
6. `Detalles de la Cuenta` validations
7. `Tus Negocios` validations
8. `Términos y Condiciones` legal page (same tab or new tab)
9. `Política de Privacidad` legal page (same tab or new tab)
10. Final PASS/FAIL report in test output

Screenshots are captured at key checkpoints and attached to the Playwright report.

## Run

From repository root:

```bash
cd automation-tests
npm test
```

To run only this scenario:

```bash
cd automation-tests
npm run test:saleads-mi-negocio
```

## Environment agnostic setup

- The test does not hardcode any SaleADS domain.
- If the browser starts on `about:blank`, pass `SALEADS_URL`:

```bash
cd automation-tests
SALEADS_URL="https://your-current-saleads-environment/login" npm run test:saleads-mi-negocio
```

- If your runner already opens the SaleADS login page before test start, `SALEADS_URL` is not required.
