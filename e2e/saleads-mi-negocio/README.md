# SaleADS Mi Negocio E2E

Playwright suite for the automation `saleads_mi_negocio_full_test`.

## What it validates

- Google login and dashboard/sidebar visibility.
- Sidebar navigation: `Negocio` -> `Mi Negocio`.
- `Agregar Negocio` modal content and cancel flow.
- `Administrar Negocios` page sections:
  - `Información General`
  - `Detalles de la Cuenta`
  - `Tus Negocios`
  - `Sección Legal`
- Legal links:
  - `Términos y Condiciones`
  - `Política de Privacidad`
- Screenshot evidence and final PASS/FAIL report.

## Environment-agnostic behavior

- The suite does **not** hardcode a domain.
- Set `SALEADS_BASE_URL` for the current environment, or open the login page first and run headed mode.

## Install

```bash
cd /workspace/e2e/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run

```bash
# headless
SALEADS_BASE_URL="https://<env-host>" npm run test:mi-negocio

# headed
SALEADS_BASE_URL="https://<env-host>" npm run test:mi-negocio:headed
```

## Evidence output

- Playwright output directory: `test-results/`
- Screenshots and JSON final report are attached to test artifacts.
- Final structured report file: `evidence/final-report.json` within test output.
