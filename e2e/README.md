# SaleADS Mi Negocio E2E

This folder contains the Playwright automation for the `saleads_mi_negocio_full_test` workflow.

## What it validates

- Google login entry point and post-login app shell visibility
- `Negocio` → `Mi Negocio` menu expansion
- `Agregar Negocio` modal required UI elements
- `Administrar Negocios` account view sections
- `Información General`, `Detalles de la Cuenta`, and `Tus Negocios` validations
- Legal links:
  - `Términos y Condiciones`
  - `Política de Privacidad`
- Final PASS/FAIL report for each required validation group

## Environment-agnostic behavior

- The test does **not** hardcode any SaleADS domain.
- If the test starts on a real login page, it uses that page directly.
- If the browser starts on `about:blank`, set one of:
  - `SALEADS_LOGIN_URL`
  - `SALEADS_BASE_URL`

## Run

```bash
cd e2e
npm run install:browsers
SALEADS_LOGIN_URL="https://your-current-env.saleads.ai/login" npm test
```

Useful alternatives:

```bash
npm run test:headed
npm run test:ui
npm run test:list
```

## Evidence artifacts

The suite captures screenshots at these checkpoints:

- Dashboard loaded after login
- Expanded `Mi Negocio` menu
- `Crear Nuevo Negocio` modal
- `Administrar Negocios` page
- `Términos y Condiciones` page
- `Política de Privacidad` page

Playwright output (screenshots/videos/report) is generated under:

- `test-results/`
- `playwright-report/`
