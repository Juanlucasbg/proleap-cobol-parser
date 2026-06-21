# SaleADS Mi Negocio full workflow test

This folder contains a standalone Playwright E2E test for:

- Google login
- Mi Negocio sidebar flow
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Legal links validation (including new-tab handling)
- Final PASS/FAIL step report

## Why this works across environments

- No domain is hardcoded in test logic.
- The start URL is injected at runtime via `SALEADS_START_URL`.
- All primary interactions prefer visible text selectors.

## Setup

```bash
cd ui-tests
npm install
npm run install:browsers
```

## Run

```bash
cd ui-tests
SALEADS_START_URL="https://<your-saleads-environment>/login" npm test
```

### Optional headed mode

```bash
cd ui-tests
SALEADS_START_URL="https://<your-saleads-environment>/login" npm run test:headed
```

## Evidence artifacts

Playwright stores screenshots and attachments in test output folders. This test captures:

- Dashboard after login
- Expanded Mi Negocio menu
- Agregar Negocio modal
- Full Administrar Negocios page
- Términos y Condiciones page
- Política de Privacidad page
- `final-report.json` attachment with PASS/FAIL by requested report field
