# SaleADS Mi Negocio full workflow test

This test automates the workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google.
2. Expand **Negocio > Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate account sections and legal links.
6. Capture screenshots and a final PASS/FAIL report.

## Run

```bash
SALEADS_LOGIN_URL="https://<your-environment-login-url>" npm run test:e2e -- tests/e2e/saleads-mi-negocio-full.spec.js
```

Optional aliases:

- `SALEADS_APP_URL`
- `BASE_URL`

The test never hardcodes a SaleADS domain and uses visible-text selectors wherever possible.

## Artifacts

- Screenshots: `test-results/saleads-mi-negocio/screenshots/`
- Final report: `test-results/saleads-mi-negocio/final-report.json`
