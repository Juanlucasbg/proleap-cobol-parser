# SaleADS Mi Negocio E2E

Playwright test for the full `Mi Negocio` workflow, including:

- Google sign-in handoff.
- Sidebar navigation (`Negocio` -> `Mi Negocio`).
- `Agregar Negocio` modal validations.
- `Administrar Negocios` sections validations.
- Legal links (`Términos y Condiciones`, `Política de Privacidad`) with same-tab/new-tab support.
- Evidence collection (screenshots + legal final URLs + JSON report).

## Environment-agnostic execution

This test does not hardcode a domain. Provide the current environment login page through environment variables.

## Requirements

- Node.js 20+ (validated with Node 22).
- Chromium dependencies for Playwright.

## Install

```bash
cd qa/saleads-e2e
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
cd qa/saleads-e2e
SALEADS_LOGIN_URL="https://<current-env>/login" npm run test:saleads-mi-negocio
```

Optional headed mode:

```bash
cd qa/saleads-e2e
SALEADS_LOGIN_URL="https://<current-env>/login" npm run test:saleads-mi-negocio:headed
```

## Artifacts

- Screenshots: `qa/saleads-e2e/artifacts/screenshots/`
- JSON report: `qa/saleads-e2e/artifacts/reports/saleads-mi-negocio-report.json`
- HTML report: `qa/saleads-e2e/artifacts/playwright-report/`
