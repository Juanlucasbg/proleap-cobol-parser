# SaleADS Mi Negocio Full Workflow Test

This folder contains an isolated Playwright E2E test for validating the **Mi Negocio** workflow in SaleADS.ai across environments (dev/staging/prod), without hardcoded domains.

## What this test validates

The test executes and reports:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal content
4. Administrar Negocios account page sections
5. Informacion General section
6. Detalles de la Cuenta section
7. Tus Negocios section
8. Terminos y Condiciones link (same tab or popup)
9. Politica de Privacidad link (same tab or popup)

It captures screenshots at key checkpoints and writes a JSON final report.

## Setup

From this directory:

```bash
npm install
npm run install:browsers
```

## Run

Set the login URL for the current environment:

```bash
export SALEADS_LOGIN_URL="https://<current-env>/login"
npm run test:saleads
```

Or run with Playwright `baseURL`:

```bash
npx playwright test tests/saleads-mi-negocio-full.spec.ts --config=playwright.config.ts --base-url "https://<current-env>/login"
```

If the runner already opens the browser on the login page, URL variables can be omitted.

## Artifacts

- `artifacts/screenshots/*.png`
- `artifacts/saleads-mi-negocio-final-report.json`
- `artifacts/playwright-report/`

