# SaleADS E2E: Mi Negocio Full Workflow

This folder contains an end-to-end Playwright test that validates the complete **Mi Negocio** workflow described in the automation prompt:

- `tests/saleads_mi_negocio_full_test.spec.js`

## Why this is environment-agnostic

- No SaleADS domain is hardcoded.
- Provide the environment URL at runtime with `SALEADS_START_URL` (or `SALEADS_URL` / `BASE_URL`).
- The test uses visible-text selectors (Spanish/English variants for login entry point) and avoids brittle URL assumptions.

## Run

```bash
cd /workspace/e2e
npx playwright install
SALEADS_START_URL="https://<your-saleads-environment>/login" npm run test:saleads-mi-negocio
```

If the Google account chooser appears, the test attempts to select:

- `juanlucasbarbiergarzon@gmail.com`

## Evidence generated

Each run stores evidence under:

- `artifacts/saleads-mi-negocio/<timestamp>/`

Including:

- Checkpoint screenshots
- Legal URLs captured during Terms/Privacy checks
- `mi-negocio-final-report.json` with PASS/FAIL per required field
