# SaleADS Mi Negocio Full Workflow

This Playwright runner validates the full "Mi Negocio" workflow requested in:

- login with Google
- open and validate Mi Negocio menu
- validate Agregar Negocio modal
- validate Administrar Negocios sections
- validate legal links (including new-tab handling)
- generate a PASS/FAIL report and screenshot evidence

## Why this is environment-agnostic

No fixed domain is hardcoded. You must provide the current environment login URL at runtime.

## Prerequisites

From this directory:

```bash
npm install
npm run install:browsers
```

## Run

```bash
SALEADS_LOGIN_URL="https://<current-env-login-page>" npm run run:workflow
```

Optional environment variables:

- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS=true|false` (default: `false`)
- `SLOW_MO_MS=250`
- `ACTION_TIMEOUT_MS=35000`
- `OUTPUT_DIR=/absolute/or/relative/path`

## Artifacts

The script writes:

- `report.json`
- `report.md`
- checkpoint screenshots

Default artifact location:

`artifacts/saleads-mi-negocio/<timestamp>/`
