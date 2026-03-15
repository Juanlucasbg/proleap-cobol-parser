# SaleADS Mi Negocio full workflow test

This repository now includes a Playwright E2E test named `saleads_mi_negocio_full_test` that validates:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal
4. Administrar Negocios view
5. Informacion General section
6. Detalles de la Cuenta section
7. Tus Negocios section
8. Terminos y Condiciones navigation
9. Politica de Privacidad navigation
10. PASS/FAIL final report generation

## Environment-agnostic execution

No domain is hardcoded. Use one of these env vars when the browser starts on `about:blank`:

- `SALEADS_URL`
- `BASE_URL`
- `APP_URL`

If your runner already opens the SaleADS login page, no URL variable is required.

## Run

```bash
npm run playwright:install
npm run test:mi-negocio
```

For headed mode:

```bash
npm run test:mi-negocio:headed
```

## Evidence output

- Screenshots: `artifacts/saleads_mi_negocio_full_test/`
- Final report: `artifacts/saleads_mi_negocio_full_test/final-report.json`
