# SaleADS Mi Negocio full workflow

This repository now includes a standalone Playwright workflow test that validates:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal content
4. Administrar Negocios page sections
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones (including new-tab handling)
9. Politica de Privacidad (including new-tab handling)
10. Final PASS/FAIL report generation

## Location

- Script: `e2e/saleads-mi-negocio-workflow.js`
- NPM script: `npm run saleads:mi-negocio:test`

## Environment variables

- `SALEADS_URL`: login page URL of the current SaleADS environment (dev/staging/prod)
- `BROWSER_WS_ENDPOINT`: optional CDP endpoint to attach to an already-open browser/tab
- `HEADLESS`: set to `false` to run headed
- `UI_WAIT_MS`: optional UI settle wait after clicks (default `1200`)
- `STEP_TIMEOUT_MS`: optional visible-wait timeout (default `15000`)

If `BROWSER_WS_ENDPOINT` is provided, the script uses the already-open browser and current tab.
Otherwise, it launches Chromium and requires `SALEADS_URL`.

## Run

```bash
npm install
npm run saleads:mi-negocio:test
```

Example:

```bash
SALEADS_URL="https://your-current-saleads-login-url" npm run saleads:mi-negocio:test
```

## Artifacts

The test writes timestamped output under:

`artifacts/saleads_mi_negocio_full_test/<timestamp>/`

Including:

- checkpoint screenshots
- `report.json`
- `report.md`
