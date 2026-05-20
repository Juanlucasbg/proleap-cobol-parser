# SaleADS Mi Negocio E2E

This suite validates the full "Mi Negocio" workflow in any SaleADS environment without hardcoding a domain.

## Required environment variable

- `SALEADS_START_URL`: login URL for the current environment (dev/staging/prod)

## Commands

```bash
npm run test:e2e
```

Headed mode:

```bash
npm run test:e2e:headed
```

## Output artifacts

- Screenshots in Playwright `test-results`
- HTML report in `playwright-report/`
- Workflow JSON report in `e2e-artifacts/saleads-mi-negocio-report.json`

The JSON file includes PASS/FAIL by requested validation block and captured legal page URLs.
