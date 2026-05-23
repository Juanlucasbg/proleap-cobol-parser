# SaleADS E2E automation

This folder contains browser automation flows for SaleADS.ai.

## Test included

- `saleads-mi-negocio-full-test.mjs` (`saleads_mi_negocio_full_test`)

## What it validates

The test executes the full workflow requested for **Mi Negocio**:

1. Google login and dashboard/sidebar visibility
2. Mi Negocio menu expansion
3. Agregar Negocio modal structure
4. Administrar Negocios view sections
5. Información General details
6. Detalles de la Cuenta labels
7. Tus Negocios section and counter
8. Términos y Condiciones legal page (new tab or same tab)
9. Política de Privacidad legal page (new tab or same tab)

The script takes screenshots at key checkpoints and writes a structured report with PASS/FAIL per section.

## Run

From repository root:

```bash
cd e2e
npm install
npx playwright install chromium
SALEADS_LOGIN_URL="https://<your-current-saleads-env>/login" npm run saleads:mi-negocio-full-test
```

## Environment variables

- `SALEADS_LOGIN_URL` (preferred): login page URL for the current environment.
- `SALEADS_BASE_URL` or `BASE_URL`: fallback URL variables.
- `HEADLESS=false`: run headed mode.

## Output artifacts

Each run writes files under:

- `e2e/artifacts/<run-id>/`
  - checkpoint screenshots
  - `final-report.json`
