# SaleADS Mi Negocio E2E

This suite validates the full **Mi Negocio** workflow after Google login without hardcoding a specific environment domain.

## Prerequisites

1. Install dependencies:

```bash
npm install
npm run playwright:install
```

2. Provide the login page URL for the current environment:

```bash
export SALEADS_LOGIN_URL="https://<current-saleads-environment>/login"
```

## Run

```bash
npm run test:e2e:saleads
```

## Output evidence

The test stores evidence under Playwright output folders:

- Checkpoint screenshots for dashboard/menu/modal/account/legal pages
- `10-mi-negocio-final-report.json` with PASS/FAIL status per required step and captured legal URLs
