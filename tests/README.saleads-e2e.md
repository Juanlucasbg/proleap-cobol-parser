# SaleADS Mi Negocio E2E

This repository now includes a Playwright test:

- `tests/saleads_mi_negocio_full_test.spec.js`

## Run

1. Install browser dependencies:

```bash
npm run pw:install
```

2. Run the test (headless):

```bash
SALEADS_BASE_URL="https://<current-saleads-env>/login" npm run test:e2e:saleads-mi-negocio
```

3. Run headed (useful for Google login flows):

```bash
HEADED=1 SALEADS_BASE_URL="https://<current-saleads-env>/login" npm run test:e2e:saleads-mi-negocio -- --headed
```

## Notes

- The test does not hardcode any domain and works with any SaleADS environment through `SALEADS_BASE_URL`.
- Selectors prioritize visible text to remain stable across environments.
- Important checkpoints are saved as screenshots in Playwright output artifacts.
- The final PASS/FAIL status report is exported as `final-report.json` inside the test output directory.
