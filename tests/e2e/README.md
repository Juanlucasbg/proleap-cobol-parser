# SaleADS Mi Negocio E2E

This folder contains the Playwright end-to-end test:

- `saleads_mi_negocio_full_test.spec.js`

## Run

1. Install dependencies:

```bash
npm install
```

2. (Optional) Set the target environment URL:

```bash
export SALEADS_URL="https://your-saleads-environment.example.com/login"
```

> The test does not hardcode a domain and can run against dev/staging/production by changing `SALEADS_URL`.

3. Execute the test:

```bash
npm run e2e:test:mi-negocio
```

## Notes

- The flow includes Google login, Mi Negocio navigation, modal validation, Administrar Negocios checks, legal links, screenshots, and final PASS/FAIL reporting.
- Screenshots are saved to `e2e-artifacts/screenshots/...`.
- The JSON final report is saved to `e2e-artifacts/reports/saleads_mi_negocio_full_test.report.json`.
