# SaleADS Mi Negocio E2E

This folder contains a Playwright end-to-end test that validates the full **Mi Negocio** workflow for SaleADS.

## Covered flow

The test `saleads_mi_negocio_full_test` validates:

1. Login with Google
2. Open **Negocio > Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate **Información General**
6. Validate **Detalles de la Cuenta**
7. Validate **Tus Negocios**
8. Validate **Términos y Condiciones** (same tab or new tab)
9. Validate **Política de Privacidad** (same tab or new tab)
10. Generate final PASS/FAIL report

It also captures screenshots at important checkpoints and records legal page final URLs.

## Environment portability

- The test does **not** hardcode a specific SaleADS domain.
- It uses visible text selectors whenever possible.
- If Playwright starts on `about:blank`, provide the environment login URL through one of these variables:
  - `SALEADS_START_URL`
  - `BASE_URL`
  - `PLAYWRIGHT_TEST_BASE_URL`

Example:

```bash
SALEADS_START_URL="https://<your-saleads-env>/login" npm run test:mi-negocio:headed
```

## Install

```bash
cd e2e
npm install
npx playwright install
```

## Run

Headless:

```bash
npm run test:mi-negocio
```

Headed:

```bash
npm run test:mi-negocio:headed
```

## Artifacts

- Screenshots: `e2e/artifacts/screenshots/<run-id>/`
- Workflow report JSON/Markdown: `e2e/artifacts/reports/`
- Playwright HTML report: `e2e/playwright-report/`
