# SaleADS Mi Negocio - Playwright E2E

Environment-agnostic E2E test for the "Mi Negocio" workflow in SaleADS.ai.

## Goals covered

- Login with Google (continue after login)
- Open `Negocio` -> `Mi Negocio`
- Validate `Agregar Negocio` modal
- Open `Administrar Negocios` and validate:
  - Informacion General
  - Detalles de la Cuenta
  - Tus Negocios
  - Seccion Legal links (`Terminos y Condiciones`, `Politica de Privacidad`)
- Capture screenshots at important checkpoints
- Emit a final PASS/FAIL report for required fields

## Install

```bash
cd /workspace/e2e/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run

The prompt says the browser starts on login page and URL changes by environment.  
Set `SALEADS_BASE_URL` for your current environment:

```bash
export SALEADS_BASE_URL="https://<your-current-saleads-host>"
```

Optional:

- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADED=1` to run headed

Run:

```bash
npm test
```

## Outputs

- Screenshots: `artifacts/screenshots/`
- Final report: `artifacts/reports/final-report.json`
- URLs captured for legal pages are saved in that report.

## Notes

- Selectors prioritize visible text and robust role/label queries.
- The test intentionally waits for UI stability after clicks.
- It handles legal links whether they open same tab or a new tab, validates content, captures evidence, and returns to app tab.
