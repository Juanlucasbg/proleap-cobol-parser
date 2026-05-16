# SaleADS E2E automation

## `saleads_mi_negocio_full_test`

This repository now includes a standalone Playwright script for the full Mi Negocio workflow:

```bash
npm run saleads:mi-negocio-full-test
```

Before first run, install browser binaries:

```bash
npx playwright install chromium
```

### Required environment variable

- `SALEADS_LOGIN_URL`: login URL for the current environment (dev/staging/prod).

### Optional environment variables

- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS` (`true` by default; set `false` for headed mode)
- `SALEADS_TEST_OUTPUT_DIR` (defaults to `artifacts/saleads_mi_negocio_full_test`)

### Outputs

Each execution creates a timestamped folder containing:

- Checkpoint screenshots
- `final-report.json` with PASS/FAIL per required validation field
- Final URLs captured for legal pages
