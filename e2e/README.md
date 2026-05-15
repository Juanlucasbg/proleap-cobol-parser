# SaleADS Mi Negocio E2E

This folder contains the Playwright test:

- `saleads-mi-negocio-full.spec.js`

## Purpose

Validate end-to-end workflow for:

1. Login with Google.
2. Navigate to **Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Terminos y Condiciones
   - Politica de Privacidad
7. Emit final PASS/FAIL report by required fields.

## Environment handling

- No hardcoded SaleADS domain is used.
- If the browser starts on `about:blank`, provide `SALEADS_APP_URL` (or `BASE_URL`).
- If the browser is already on the login page, the test continues from there.

## Run

```bash
npm run test:e2e:saleads-mi-negocio
```

To only check discovery:

```bash
npm run test:e2e:list
```

## Evidence

The test captures screenshots at key checkpoints and writes a final report attachment:

- `10-final-report.json`
