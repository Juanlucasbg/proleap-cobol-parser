## SaleADS Mi Negocio E2E test

This folder contains `saleads_mi_negocio_full_test`, a Playwright test that validates:

1. Google login flow
2. Mi Negocio menu expansion
3. Agregar Negocio modal checks
4. Administrar Negocios view checks
5. Informacion General checks
6. Detalles de la Cuenta checks
7. Tus Negocios checks
8. Terminos y Condiciones navigation + URL capture
9. Politica de Privacidad navigation + URL capture
10. Final PASS/FAIL report generation

### Requirements

- Node.js 18+
- Playwright Chromium browser:

```bash
npm run playwright:install
```

### Environment variables

Set one of:

- `SALEADS_START_URL`
- `SALEADS_LOGIN_URL`
- `SALEADS_URL`

The value should be the login URL for the target SaleADS environment (dev/staging/prod).

### Run

```bash
npm run test:e2e:saleads
```

### Output evidence

Playwright output artifacts are generated in `test-results/` and include:

- Checkpoint screenshots
- Final JSON report (`saleads_mi_negocio_final_report.json`)
- Captured legal URLs for terms and privacy
