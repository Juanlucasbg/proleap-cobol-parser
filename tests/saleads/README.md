# SaleADS Mi Negocio E2E

This folder contains the full workflow test requested in `saleads_mi_negocio_full_test`.

## Run

```bash
SALEADS_START_URL="https://<current-environment>/login" npm run e2e:saleads-mi-negocio
```

Headed mode:

```bash
SALEADS_START_URL="https://<current-environment>/login" npm run e2e:saleads-mi-negocio:headed
```

## Notes

- No domain is hardcoded.
- Selectors prioritize visible text.
- The test captures screenshots at workflow checkpoints.
- Final PASS/FAIL status by section is attached as `final-report.json`.
