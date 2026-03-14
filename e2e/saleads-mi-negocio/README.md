# SaleADS Mi Negocio Full Workflow Test

This folder contains the Playwright end-to-end test:

- `saleads_mi_negocio_full_test`

It validates the full workflow requested for SaleADS.ai:

1. Login with Google.
2. Open **Mi Negocio** menu and validate options.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios** and validate sections.
5. Validate **Información General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Términos y Condiciones** (+ URL evidence).
9. Validate **Política de Privacidad** (+ URL evidence).
10. Generate final PASS/FAIL report.

## Environment-agnostic behavior

- No domain is hardcoded.
- Provide target environment via `SALEADS_URL` (or `BASE_URL`).
- If URL is not provided, the test assumes the browser starts on the login page.

## Setup

```bash
cd /workspace/e2e/saleads-mi-negocio
npm install
npx playwright install
```

## Run

```bash
SALEADS_URL="https://<your-environment>" npm test
```

### Optional variables

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS=false` to run with browser UI

## Evidence generated

- Checkpoint screenshots are attached to the Playwright test output.
- A JSON final report is saved as `mi-negocio-final-report.json` in the Playwright output folder.
- Console output includes:
  - `FINAL_REPORT` (PASS/FAIL map)
  - `TERMINOS_URL`
  - `PRIVACIDAD_URL`
