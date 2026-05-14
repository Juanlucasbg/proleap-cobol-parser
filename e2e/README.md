# SaleADS Mi Negocio E2E workflow

This folder contains a Playwright test that automates the full **Mi Negocio** workflow on SaleADS.ai:

- Google login (and account selection when shown)
- Menu expansion in sidebar
- "Agregar Negocio" modal validation
- "Administrar Negocios" page validation
- Legal links validation for Términos and Política (including popup/new tab support)
- Checkpoint screenshots and final PASS/FAIL report

## Requirements

- Node.js 18+ (Node 22 recommended)
- Playwright browsers installed

## Install

```bash
cd e2e
npm install
npx playwright install
```

## Run

Use an environment URL rather than a hardcoded domain:

```bash
cd e2e
SALEADS_LOGIN_URL="https://<current-environment>/login" npm run test:mi-negocio
```

For visual debugging:

```bash
cd e2e
HEADLESS=false SALEADS_LOGIN_URL="https://<current-environment>/login" npm run test:mi-negocio
```

## Output artifacts

Playwright stores outputs in `e2e/test-results/` and `e2e/playwright-report/`.

Important artifacts include:

- Checkpoint screenshots for dashboard, menu, modal, account page, and legal pages
- `mi-negocio-final-report.json` with PASS/FAIL for each validation field
