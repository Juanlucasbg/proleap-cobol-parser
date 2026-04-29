# SaleADS E2E workflow tests

This folder contains end-to-end UI tests for SaleADS workflows using Playwright.

## saleads_mi_negocio_full_test

Main test file: `saleads-mi-negocio.spec.ts`

### Execution assumptions

- Browser may already be on the SaleADS login page.
- If the browser starts on `about:blank`, set `SALEADS_LOGIN_URL` to the login URL of the target environment.
- The test is URL/domain agnostic and relies on visible text and role-based selectors.

### Run

```bash
npm run test:e2e -- --grep "saleads_mi_negocio_full_test"
```

### Output and evidence

- Checkpoint screenshots are saved in Playwright test output artifacts.
- A JSON summary with PASS/FAIL per validation and legal URLs is saved as:
  - `.../evidence/final-report.json` (inside test output directory)
