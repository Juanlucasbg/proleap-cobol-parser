# SaleADS Mi Negocio Full Workflow E2E

This folder contains a Playwright test that automates and validates the complete **Mi Negocio** workflow, including:

- Login with Google
- Sidebar and menu expansion checks
- "Agregar Negocio" modal validations
- "Administrar Negocios" account sections validations
- Legal links ("Términos y Condiciones" and "Política de Privacidad")
- Checkpoint screenshots and final JSON report

## Why this is environment-agnostic

The test does **not** hardcode any domain. You must provide the environment URL at runtime:

- `SALEADS_LOGIN_URL`, or
- `SALEADS_BASE_URL`

## Setup

```bash
cd e2e
npm install
npx playwright install
```

## Run

```bash
cd e2e
SALEADS_LOGIN_URL="https://<your-saleads-environment>/login" npm test
```

Optional:

- `UI_SETTLE_MS` (default: `900`) to increase/decrease UI wait time after clicks.

## Outputs

Playwright outputs include:

- HTML report: `e2e/playwright-report/`
- Test artifacts: `e2e/test-results/`
- Attached checkpoint screenshots
- Attached `final-report.json` with:
  - PASS/FAIL by required section
  - Captured legal URLs
  - Error list
