# SaleADS Mi Negocio E2E

This repository now includes an automated Playwright test:

- `tests/saleads_mi_negocio_full_test.spec.js`

## Purpose

Validate the full `Mi Negocio` workflow in SaleADS after Google login, including:

- Login and dashboard/sidebar visibility
- `Mi Negocio` menu expansion
- `Agregar Negocio` modal checks
- `Administrar Negocios` section checks
- Legal links (`Términos y Condiciones`, `Política de Privacidad`) with new-tab handling
- Evidence screenshots and final PASS/FAIL report

## Run

Install dependencies:

```bash
npm install
npx playwright install chromium
```

Run tests:

```bash
npm run test:e2e
```

Optional, if automation starts from `about:blank` and must navigate first:

```bash
SALEADS_BASE_URL="https://<current-environment-host>" npm run test:e2e
```

## Artifacts

Playwright stores outputs in `test-results/`:

- checkpoint screenshots
- `saleads_mi_negocio_final_report.json` with PASS/FAIL per requested field
- final URLs for legal pages
