# SaleADS Mi Negocio Full Workflow Test

This folder contains an isolated Playwright-based automation for the workflow:
`saleads_mi_negocio_full_test`.

## What it validates

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal checks
4. Administrar Negocios sections
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones page
9. Politica de Privacidad page
10. Final PASS/FAIL report by step

The test is designed to be environment-agnostic:

- No hardcoded SaleADS domain.
- Uses visible-text selectors where possible.
- Supports legal links that open in the same tab or a popup.

## Run

```bash
cd automation/saleads-mi-negocio
npm install
npm test
```

## Configuration

- `SALEADS_LOGIN_URL` (optional): login URL for current environment.
  - If omitted, the script records a note and still attempts to continue from current page state.
- `SALEADS_HEADLESS` (optional): `false` to run headed. Default is headless.
- `SALEADS_TIMEOUT_MS` (optional): action timeout. Default `30000`.
- `SALEADS_UI_SETTLE_MS` (optional): post-click settle wait. Default `1000`.

## Artifacts

Generated under `automation/saleads-mi-negocio/artifacts/`:

- `report.json`: final structured report including step statuses and captured URLs.
- `screenshots/*.png`: evidence screenshots at required checkpoints.
