# SaleADS UI Workflow Tests

This folder contains a portable Playwright workflow test:

- `tests/saleads-mi-negocio-full.spec.ts`

## What it validates

The test executes end-to-end validation for:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal
4. Administrar Negocios page
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones (including external/new-tab handling)
9. Política de Privacidad (including external/new-tab handling)
10. Final PASS/FAIL report output

It captures screenshots at key checkpoints and generates:

- `artifacts/saleads_mi_negocio_full_test/<timestamp>/final-report.json`
- `artifacts/saleads_mi_negocio_full_test/<timestamp>/final-report.md`

## Run

```bash
cd ui-tests
npm install
npm run install:browsers
npm run test:saleads-mi-negocio
```

## Environment handling

- No SaleADS URL is hardcoded.
- If your runner starts on the login page already, the test will continue from there.
- If needed, pass `SALEADS_LOGIN_URL` dynamically per environment:

```bash
SALEADS_LOGIN_URL="https://<current-environment-login-page>" npm run test:saleads-mi-negocio
```
