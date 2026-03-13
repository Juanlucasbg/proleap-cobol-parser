# SaleADS QA Workflows

This folder contains Playwright end-to-end workflows for SaleADS.ai.

## Workflow included

- `tests/saleads-mi-negocio-full.spec.ts`: full "Mi Negocio" validation flow with:
  - Google login handling (including optional account selector click for `juanlucasbarbiergarzon@gmail.com`)
  - navigation through **Negocio > Mi Negocio**
  - modal and account sections validation
  - legal links validation (same tab or popup/new tab)
  - required checkpoint screenshots
  - final PASS/FAIL JSON report

## Environment-agnostic execution

The test does not hardcode any domain. It supports:

1. Browser already on login page (as per flow requirement), or
2. URL provided through one of:
   - `SALEADS_LOGIN_URL`
   - `SALEADS_URL`
   - `BASE_URL`

## Run

```bash
cd qa
npm install
npx playwright install --with-deps chromium
npm run test:mi-negocio
```

## Outputs

- Screenshots: `test-results/saleads_mi_negocio_full_test/screenshots/`
- Final report: `test-results/saleads_mi_negocio_full_test/final-report.json`
