# SaleADS E2E tests

This repository includes a Playwright end-to-end test for the Mi Negocio workflow:

- `tests/saleads_mi_negocio_full_test.spec.ts`

## Run

1. Install browser dependencies (one-time):

```bash
npm run test:e2e:install
```

2. Run the test:

```bash
SALEADS_LOGIN_URL="https://<your-environment>/login" npm run test:e2e:headed -- --grep "saleads_mi_negocio_full_test"
```

Notes:

- The test is environment-agnostic and does not hardcode a specific domain.
- If `SALEADS_LOGIN_URL` is not set, the test expects to start from the login page in the current browser page.
- Google account selection supports `juanlucasbarbiergarzon@gmail.com` when the chooser appears.

## Evidence

Evidence files are generated in:

- `test-results/saleads-mi-negocio/`
  - checkpoint screenshots
  - `final-report.json` with PASS/FAIL per section and final legal URLs
