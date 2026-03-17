# SaleADS Mi Negocio Full Workflow Test

This folder contains an isolated Playwright test named:

- `saleads_mi_negocio_full_test`

It validates the complete "Mi Negocio" module workflow, not only login.

## Why this is environment-agnostic

- No hardcoded SaleADS domain is used.
- The login entry point is provided at runtime:
  - `SALEADS_LOGIN_URL`, or
  - `SALEADS_BASE_URL`
- Selectors prioritize visible text (`getByText`, `getByRole`) to stay robust across environments.

## Install

```bash
npm install
npm run install:browsers
```

## Run

```bash
SALEADS_LOGIN_URL="https://<your-saleads-login-page>" npm run test:mi-negocio
```

Headed mode:

```bash
HEADLESS=false SALEADS_LOGIN_URL="https://<your-saleads-login-page>" npm run test:mi-negocio:headed
```

## Evidence and report

The test captures screenshots for critical checkpoints and writes a JSON final report with PASS/FAIL per requested section, including final URLs for legal pages.

Generated artifacts are available under Playwright's `test-results/` output.
