# SaleADS E2E tests

This directory contains browser end-to-end tests for SaleADS workflows.

## Mi Negocio full workflow test

Test file: `e2e/saleads_mi_negocio_full_test.spec.ts`

### Run

```bash
SALEADS_BASE_URL="https://<your-saleads-environment>" npm run test:e2e:mi-negocio
```

Notes:

- The test is environment-agnostic and does not hardcode any SaleADS domain.
- If Google account chooser appears, it selects `juanlucasbarbiergarzon@gmail.com`.
- The test captures screenshots at key checkpoints and generates a final JSON report attachment.
