# SaleADS end-to-end test

This suite contains the `saleads_mi_negocio_full_test` Playwright scenario requested by automation.

## Run

1. Install dependencies:

```bash
npm install
```

2. Provide the current environment login URL (dev/staging/prod):

```bash
export SALEADS_URL="https://your-current-saleads-environment/login"
```

3. Execute the test:

```bash
npm run test:e2e -- tests/e2e/saleads-mi-negocio-full-test.spec.ts
```

## Evidence produced

- Checkpoint screenshots for dashboard, menu, modal, account page and legal pages.
- JSON final report with PASS/FAIL per requested validation field and captured legal URLs.
