# SaleADS Mi Negocio full workflow E2E

This folder contains an end-to-end Playwright test for:

- Google login
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios page and sections validation
- Legal links validation (including new tab handling)
- Final PASS/FAIL report for each requested section

## Prerequisites

1. Node.js 18+ installed.
2. Playwright browsers installed:

```bash
npx playwright install
```

## Run

From this folder:

```bash
npm test
```

Optional environment variable:

- `SALEADS_START_URL`: login page URL for the current environment (dev/staging/prod).

No domain is hardcoded in the test.

## Output

- Checkpoint screenshots are stored in Playwright test output folders.
- On failures, trace/video/screenshot are retained.
- Final section-level report is printed in the console as a table.
