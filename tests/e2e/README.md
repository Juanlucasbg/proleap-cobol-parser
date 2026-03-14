# SaleADS E2E tests

## Mi Negocio full workflow

This suite includes `saleads_mi_negocio_full_test.spec.js`, which validates:

- Google login flow
- Sidebar `Negocio` -> `Mi Negocio` expansion
- `Agregar Negocio` modal content
- `Administrar Negocios` sections
- Legal pages (`Términos y Condiciones`, `Política de Privacidad`) with popup/new-tab handling
- Checkpoint screenshots and final PASS/FAIL report attachment

## Run

Set one of these environment variables to avoid hardcoding any environment URL:

- `SALEADS_LOGIN_URL`
- `SALEADS_BASE_URL`
- `BASE_URL`

Then run:

```bash
npx playwright test tests/e2e/saleads_mi_negocio_full_test.spec.js
```

Optional headed mode:

```bash
npx playwright test tests/e2e/saleads_mi_negocio_full_test.spec.js --headed
```
