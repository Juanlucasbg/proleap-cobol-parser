# SaleADS Mi Negocio Full Workflow (Playwright)

This package contains the automated test `saleads_mi_negocio_full_test` for:

1. Google login.
2. Mi Negocio menu validation.
3. Agregar Negocio modal validation.
4. Administrar Negocios sections validation.
5. Legal links validation (including new-tab handling).
6. Final PASS/FAIL report generation.

## Requirements

- Node.js 18+
- Playwright Chromium browser

Install dependencies and browsers:

```bash
npm install
npx playwright install chromium
```

## Run the test

The test is environment-agnostic. Set the login URL of the current SaleADS environment with an environment variable:

```bash
SALEADS_LOGIN_URL="https://your-current-env.example.com/login" npm test
```

Alternative accepted variables are `SALEADS_URL` or `BASE_URL`.

## Evidence generated

Playwright test artifacts include:

- Checkpoint screenshots:
  - dashboard loaded
  - Mi Negocio expanded menu
  - Agregar Negocio modal
  - Administrar Negocios page
  - Términos y Condiciones page
  - Política de Privacidad page
- `final-report.json` with PASS/FAIL status for each required validation field and captured legal URLs.
