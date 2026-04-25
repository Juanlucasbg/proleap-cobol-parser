# SaleADS E2E tests

This repository now includes a standalone Playwright E2E test for the SaleADS
"Mi Negocio" workflow:

- Test file: `tests/saleads-mi-negocio-full.spec.ts`
- Report output: `artifacts/saleads-mi-negocio/final-report.json`
- Screenshots: `artifacts/saleads-mi-negocio/screenshots/*.png`

## Install browser binaries

```bash
npx playwright install
```

## Run

Assume the browser starts on the SaleADS login page (as requested in the
automation prompt), then run:

```bash
npm run test:e2e -- tests/saleads-mi-negocio-full.spec.ts
```

For local debugging:

```bash
npm run test:e2e:headed -- tests/saleads-mi-negocio-full.spec.ts
```

## Notes

- The test does not depend on a specific URL/domain.
- Selectors prioritize visible text and ARIA roles.
- If legal links open in a new tab, the test validates content there and
  returns to the app tab.
