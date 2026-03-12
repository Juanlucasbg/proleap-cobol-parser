# SaleADS Mi Negocio Full Test

This folder contains an isolated Playwright automation script for:

- `saleads_mi_negocio_full_test`

## What it validates

1. Login with Google and app sidebar visibility.
2. `Mi Negocio` menu expansion.
3. `Agregar Negocio` modal fields and actions.
4. `Administrar Negocios` account sections.
5. `Información General` required elements.
6. `Detalles de la Cuenta` required labels.
7. `Tus Negocios` required elements.
8. `Términos y Condiciones` (including URL capture and screenshot).
9. `Política de Privacidad` (including URL capture and screenshot).

It generates:

- `final-report.json`
- `final-report.txt`
- checkpoint screenshots

under:

- `artifacts/saleads_mi_negocio_full_test/<timestamp>/`

## Run

```bash
cd /workspace/e2e/saleads-mi-negocio
npm install
npm run saleads:mi-negocio:full-test
```

## Environment options

- `SALEADS_START_URL` (recommended when not attaching to an existing browser)
  - Example: `https://<current-env>/login`
- `BROWSER_WS_ENDPOINT` (optional, attach to an already-open browser via CDP)
- `HEADLESS` (`true` by default; set `false` to watch interactions)
- `SLOW_MO_MS` (default `100`)
- `UI_SETTLE_MS` (default `1200`)
- `ARTIFACTS_DIR` (optional custom output directory)

## Notes

- The script intentionally avoids domain-specific checks and validates mostly by visible text.
- If legal links open in a new tab, it validates there and returns to the app tab automatically.
