# SaleADS Mi Negocio Full Workflow Test

This folder contains a Playwright end-to-end test that validates the full
`Mi Negocio` workflow in SaleADS after Google login.

The test id is:

- `saleads_mi_negocio_full_test`

## What it validates

The workflow covers:

1. Google login (and continue after login)
2. Sidebar `Negocio` -> `Mi Negocio` expansion
3. `Agregar Negocio` modal validation
4. `Administrar Negocios` page validation
5. `Información General`
6. `Detalles de la Cuenta`
7. `Tus Negocios`
8. `Términos y Condiciones` (new tab or same tab)
9. `Política de Privacidad` (new tab or same tab)
10. Final PASS/FAIL report per step

It also captures screenshots at required checkpoints and writes a JSON report
under `artifacts/reports`.

## Environment-agnostic behavior

- No domain is hardcoded.
- The script assumes the browser is already on a SaleADS login page, or it can
  start from `SALEADS_START_URL`/`BASE_URL` when provided.
- Element targeting is based primarily on visible text.

## Run locally

From this folder:

```bash
npm install
npx playwright install
npm run test:e2e
```

Optional (if you need to start from a specific environment URL):

```bash
SALEADS_START_URL="https://your-env.example.com/login" npm run test:e2e
```

Run headed:

```bash
npm run test:e2e:headed
```

## Output artifacts

- Screenshots: `artifacts/screenshots/*.png`
- Final report: `artifacts/reports/saleads_mi_negocio_final_report_<run-id>.json`
- Playwright HTML report: `playwright-report/`
