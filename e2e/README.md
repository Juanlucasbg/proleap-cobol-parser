# SaleADS E2E Tests

This folder contains UI end-to-end tests for SaleADS workflows using Playwright.

## Implemented test

- `tests/saleads-mi-negocio-full.spec.ts`
  - Test name: `saleads_mi_negocio_full_test`
  - Covers:
    1. Login with Google (or reusing active session)
    2. Mi Negocio menu expansion
    3. Agregar Negocio modal validation
    4. Administrar Negocios view validation
    5. Informacion General validation
    6. Detalles de la Cuenta validation
    7. Tus Negocios validation
    8. Terminos y Condiciones validation (same tab or new tab)
    9. Politica de Privacidad validation (same tab or new tab)
    10. Final PASS/FAIL report with legal URLs

## Environment-agnostic behavior

- The test **does not hardcode any SaleADS domain**.
- It supports two startup modes:
  - **Recommended:** set `SALEADS_URL` to the current environment login page.
  - **Alternative:** launch Playwright on an already opened SaleADS login page and do not set `SALEADS_URL`.

## Prerequisites

- Node.js 18+ (or compatible modern Node version)
- Playwright browser binaries installed

## Install

From the repo root:

```bash
cd e2e
npm install
npx playwright install --with-deps
```

## Run

Headless:

```bash
cd e2e
SALEADS_URL="https://<current-saleads-env>/login" npm test
```

Headed:

```bash
cd e2e
SALEADS_URL="https://<current-saleads-env>/login" npm run test:headed
```

## Evidence and report artifacts

- HTML report: `e2e/playwright-report/`
- JSON report: `e2e/test-results/results.json`
- Checkpoint screenshots are attached to the Playwright test output for:
  - Dashboard loaded
  - Mi Negocio menu expanded
  - Agregar Negocio modal
  - Administrar Negocios page
  - Terminos y Condiciones
  - Politica de Privacidad
- Final report attachment: `final-report.json` (contains PASS/FAIL per requested section and captured legal URLs)

## Notes

- Selectors prefer visible text and role-based locators.
- After each click the test waits for UI stabilization (`domcontentloaded`, `networkidle`, short settle wait).
- If legal links open in a new tab, the test validates the new tab and returns to the app tab.
