# SaleADS Mi Negocio full workflow test

This script automates the full **Mi Negocio** workflow requested in the `saleads_mi_negocio_full_test` scenario.

## Requirements

- Node.js 18+ recommended
- Playwright dependency installed (`npm install`)
- Chromium browser installed for Playwright:

```bash
npx playwright install chromium
```

## Environment-agnostic execution

The script does **not** hardcode any SaleADS domain.

Set the login URL for your current environment (dev/staging/prod) via environment variable:

```bash
export SALEADS_LOGIN_URL="https://<your-saleads-environment>/login"
```

Optional:

```bash
export HEADLESS=false     # to watch execution
export SLOW_MO_MS=250     # slow down clicks for debugging
```

## Run

```bash
npm run test:mi-negocio
```

## Evidence output

Generated under `artifacts/`:

- `artifacts/screenshots/*.png` checkpoint screenshots
- `artifacts/final-report.json` final PASS/FAIL report, notes, and legal final URLs

## Report fields

The final report includes:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
