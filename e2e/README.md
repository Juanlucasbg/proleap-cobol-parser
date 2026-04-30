# SaleADS Mi Negocio E2E Workflow

This folder contains an environment-agnostic Playwright test for the SaleADS
"Mi Negocio" module workflow.

## What this test validates

The test covers the full requested flow:

1. Login with Google
2. Open Mi Negocio sidebar menu
3. Validate "Agregar Negocio" modal
4. Open "Administrar Negocios"
5. Validate "Informacion General"
6. Validate "Detalles de la Cuenta"
7. Validate "Tus Negocios"
8. Validate "Terminos y Condiciones" (new tab or same tab)
9. Validate "Politica de Privacidad" (new tab or same tab)
10. Emit PASS/FAIL summary per step

It intentionally does **not** hardcode any base URL. It assumes the browser is
already on the environment login page.

## Setup

From repository root:

```bash
npm install --prefix e2e
npx --prefix e2e playwright install chromium --with-deps
```

## Run

```bash
npm --prefix e2e run test
```

Optionally run headed:

```bash
PW_HEADED=1 npm --prefix e2e run test -- --headed
```

## Artifacts

- Screenshots: `e2e/screenshots/`
- Playwright output: `e2e/test-results/`, `e2e/playwright-report/`

Each checkpoint screenshot is timestamped and attached to the test report.
