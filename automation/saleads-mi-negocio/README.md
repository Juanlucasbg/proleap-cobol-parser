# SaleADS Mi Negocio Full Workflow Test

This folder contains an isolated Playwright E2E test for the `saleads_mi_negocio_full_test` scenario.

## What it validates

The test runs the complete workflow requested:

1. Login with Google.
2. Open sidebar `Negocio > Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (same tab or popup).
9. Validate `Política de Privacidad` (same tab or popup).
10. Produce a final PASS/FAIL report for all fields.

It also captures screenshots at key checkpoints and stores legal-page final URLs in the JSON report.

## Environment-agnostic configuration

No domain is hardcoded.

Set the login page for the current environment through an environment variable:

```bash
export SALEADS_LOGIN_URL="https://<current-environment-login-page>"
```

Optional:

```bash
export SALEADS_GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com"
```

## Run

From this folder:

```bash
npm install
npx playwright install --with-deps chromium
npm test
```

Headed mode:

```bash
npm run test:headed
```

## Artifacts

- Screenshots: `test-results/saleads-mi-negocio/screenshots/`
- Final report: `test-results/saleads-mi-negocio/final-report.json`
- HTML Playwright report: `playwright-report/`
