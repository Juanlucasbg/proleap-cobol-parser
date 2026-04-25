# SaleADS Mi Negocio E2E

This folder contains an environment-agnostic Playwright test for:

- Google login flow (including optional Google account picker handling)
- Mi Negocio menu validations
- Agregar Negocio modal validations
- Administrar Negocios page validations
- Información General, Detalles de la Cuenta, and Tus Negocios validations
- Términos y Condiciones and Política de Privacidad legal links (same-tab or new-tab)
- Checkpoint screenshots and a final per-step PASS/FAIL JSON report

## Test implemented

- `tests/saleads-mi-negocio-full.spec.ts`
- Test name: `saleads_mi_negocio_full_test`

## Prerequisites

- Node.js 20+ recommended
- Browser dependencies for Playwright

## Install

```bash
cd ui-e2e
npm install
npx playwright install --with-deps chromium
```

## Run

You can run in any SaleADS environment by passing the login URL at runtime:

```bash
cd ui-e2e
SALEADS_START_URL="https://<current-env-host>/login" npm run test:mi-negocio
```

If your runner already opens the page before test execution, you can omit `SALEADS_START_URL`.

Optional:

- `SALEADS_BASE_URL`: used as Playwright base URL and fallback start URL
- `HEADED=true`: run headed mode

Examples:

```bash
cd ui-e2e
SALEADS_BASE_URL="https://<current-env-host>" SALEADS_START_URL="https://<current-env-host>/login" npm run test:mi-negocio
```

```bash
cd ui-e2e
HEADED=true SALEADS_START_URL="https://<current-env-host>/login" npm run test:mi-negocio
```

## Evidence artifacts

For each run, artifacts are saved in:

- `ui-e2e/screenshots/<run-id>/`

This includes:

- Checkpoint screenshots
- `final-report.json` with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad

