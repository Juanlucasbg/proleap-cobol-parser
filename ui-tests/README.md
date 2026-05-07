# SaleADS UI Tests

This folder contains an environment-agnostic Playwright test for the full **Mi Negocio** workflow:

- Google login
- `Negocio` > `Mi Negocio` menu expansion
- `Agregar Negocio` modal validation
- `Administrar Negocios` account sections validation
- Legal links validation (`Términos y Condiciones`, `Política de Privacidad`)
- Screenshot checkpoints and final PASS/FAIL matrix report

## Prerequisites

- Node.js 18+
- npm
- Playwright Chromium browser

Install dependencies:

```bash
cd ui-tests
npm install
npm run install:browsers
```

## Configuration

The test does not hardcode a domain. Provide the login page URL for whichever environment you want to run:

- `SALEADS_LOGIN_URL` (required for unattended execution)
- `SALEADS_GOOGLE_ACCOUNT` (optional, default: `juanlucasbarbiergarzon@gmail.com`)

Example:

```bash
cd ui-tests
SALEADS_LOGIN_URL="https://<your-env>/login" npm run test:saleads-mi-negocio
```

## Evidence and Report

Each run stores evidence in:

```text
ui-tests/artifacts/saleads_mi_negocio_full_test/<timestamp>/
```

Artifacts include:

- Step screenshots
- `final-report.json` with PASS/FAIL status per validation field
- Captured legal document URLs
