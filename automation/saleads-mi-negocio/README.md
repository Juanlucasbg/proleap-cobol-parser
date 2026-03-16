# SaleADS Mi Negocio Full Test

This folder contains an isolated Playwright automation for the workflow:

- Login with Google
- Expand **Mi Negocio**
- Validate **Agregar Negocio** modal
- Open **Administrar Negocios**
- Validate account sections
- Validate **Términos y Condiciones** and **Política de Privacidad** (popup or same tab)
- Save screenshots and a final PASS/FAIL report

## Why isolated?

This repository is a Java/Maven project, so the browser E2E test is intentionally kept separate to avoid impacting the existing build and test suite.

## Requirements

- Node.js 18+
- Access to a SaleADS environment login page URL

## Install

```bash
npm install
npm run install:browsers
```

## Run

```bash
SALEADS_LOGIN_URL="https://<your-saleads-login-page>" \
SALEADS_GOOGLE_ACCOUNT="juanlucasbarbiergarzon@gmail.com" \
npm run test:saleads-mi-negocio
```

If your automation runner already opens the SaleADS login page before executing this script, `SALEADS_LOGIN_URL` can be omitted.

### Optional env vars

- `HEADLESS=false` to run with visible browser UI
- `SALEADS_GOOGLE_ACCOUNT` defaults to `juanlucasbarbiergarzon@gmail.com`

## Output artifacts

Each run writes artifacts under:

`artifacts/saleads_mi_negocio_full_test_<timestamp>/`

Includes:

- Step screenshots (dashboard, expanded menu, modal, account page, legal pages)
- `saleads_mi_negocio_full_test_report.json` with:
  - PASS/FAIL status for each requested report field
  - captured legal URLs
  - per-step error details (if any)
