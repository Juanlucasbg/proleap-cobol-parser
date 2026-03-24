# SaleADS Mi Negocio full workflow test

This folder contains an environment-agnostic Playwright automation for:

- Login with Google.
- Mi Negocio menu validations.
- Agregar Negocio modal validations.
- Administrar Negocios sections validations.
- Información General / Detalles de la Cuenta / Tus Negocios validations.
- Términos y Condiciones + Política de Privacidad validations.
- Screenshot evidence and final PASS/FAIL report.

## Why it is environment-agnostic

- No hardcoded SaleADS domain.
- You can either:
  - provide a target URL at runtime, or
  - attach to an already-open browser/page via CDP.
- Selectors prioritize visible text and role-based lookups.

## Setup

```bash
cd qa/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run options

### Option A: Open URL directly

```bash
SALEADS_URL="https://<your-saleads-environment>" npm test
```

### Option B: Attach to an already-open browser/page (recommended when login page is already open)

1. Start Chromium/Chrome with remote debugging enabled, and navigate manually to the SaleADS login page.
2. Run:

```bash
PLAYWRIGHT_CDP_URL="http://127.0.0.1:9222" npm test
```

## Useful runtime flags

- `HEADLESS=false` to watch execution.
- `ARTIFACTS_DIR=/custom/path` to control output directory.

Example:

```bash
HEADLESS=false SALEADS_URL="https://<env>" npm test
```

## Evidence and report output

Each run generates:

- Screenshots at critical checkpoints.
- `final-report.json` with per-step PASS/FAIL + details.
- Captured final URLs for:
  - Términos y Condiciones
  - Política de Privacidad

Default output path:

`qa/saleads-mi-negocio/artifacts/<timestamp>/`

