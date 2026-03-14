# SaleADS Mi Negocio full workflow test

This repository now includes a Playwright test named:

- `tests/saleads_mi_negocio_full_test.spec.js`

## What it validates

The test implements the full workflow requested for `saleads_mi_negocio_full_test`:

1. Login with Google (including optional account-picker selection)
2. Expand `Negocio` -> `Mi Negocio`
3. Validate the `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Información General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Términos y Condiciones` (new tab or same tab)
9. Validate `Política de Privacidad` (new tab or same tab)
10. Emit final PASS/FAIL report per required field

Screenshots are captured at key checkpoints and attached to Playwright test artifacts.

## Environment-agnostic execution

No domain is hard-coded.

- If the browser is already on the SaleADS login page, the test starts from there.
- If not, provide one of these environment variables:
  - `SALEADS_URL`
  - `SALEADS_BASE_URL`
  - `BASE_URL`

## Run

Install dependencies:

```bash
npm install
```

Run only this workflow:

```bash
npm run test:saleads-mi-negocio
```

Run headed mode:

```bash
npm run test:saleads-mi-negocio:headed
```
