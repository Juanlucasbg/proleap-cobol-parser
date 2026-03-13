# SaleADS E2E automation

This folder contains the Playwright test:

- `saleads-mi-negocio-full.spec.ts` (`saleads_mi_negocio_full_test`)

## What it validates

The test automates and validates:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal
4. Administrar Negocios page sections
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones
9. Politica de Privacidad

It captures screenshots at key checkpoints and writes a final JSON report with PASS/FAIL per requested field.

## Run

Install dependencies:

```bash
npm install
npx playwright install --with-deps chromium
```

Run test (URL from environment variable):

```bash
SALEADS_URL="https://your-environment.example" npm run test:e2e -- --grep saleads_mi_negocio_full_test
```

If `SALEADS_URL` is omitted, the test expects to already be on the SaleADS login page (non-blank URL).
