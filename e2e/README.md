# SaleADS E2E Automations

This folder contains standalone browser automation for SaleADS workflows.

## Mi Negocio full workflow

Script: `scripts/saleads_mi_negocio_full_test.mjs`

### Run

```bash
cd e2e
npm install
npx playwright install chromium
SALEADS_LOGIN_URL="https://<current-saleads-env>/login" npm run test:saleads:mi-negocio
```

### Environment variables

- `SALEADS_LOGIN_URL` (required): login URL for the current SaleADS environment.
- `HEADLESS` (optional): set to `false` to run headed (default is headless).

### Output

Artifacts are generated under:

- `e2e/artifacts/saleads_mi_negocio_full_test_<timestamp>/`
  - Screenshots for key checkpoints
  - `final-report.json` with PASS/FAIL for each required validation field
