# SaleADS E2E - Mi Negocio Workflow

This folder contains a Playwright end-to-end test for:

- Login with Google
- Mi Negocio menu workflow validation
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Legal links validation (with popup/new-tab support)
- Evidence capture (screenshots + final JSON report)

## Test file

- `tests/saleads-mi-negocio.spec.js`

## Environment-agnostic behavior

The test does not hardcode a domain. It assumes the browser is already at the SaleADS login page for the target environment, as requested.

If needed, you can set:

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)

## Run

From repository root:

```bash
cd e2e
npx playwright install
npm run test:saleads:mi-negocio
```

Headed mode:

```bash
cd e2e
npm run test:headed -- tests/saleads-mi-negocio.spec.js
```

## Artifacts

Generated under:

- `e2e/test-results/saleads-mi-negocio/`
  - checkpoint screenshots
  - `final-report.json` with PASS/FAIL per requested section

Playwright HTML report:

- `e2e/playwright-report/`
