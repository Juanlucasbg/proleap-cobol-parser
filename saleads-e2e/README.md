# SaleADS Mi Negocio Full Test

This folder contains an end-to-end Playwright test named:

- `saleads_mi_negocio_full_test`

## What it validates

The test automates the complete workflow requested for SaleADS:

1. Login with Google.
2. Open `Negocio` -> `Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (including new-tab handling).
9. Validate `Política de Privacidad` (including new-tab handling).
10. Emit final PASS/FAIL report with captured legal URLs.

The implementation avoids hardcoding any SaleADS domain and uses runtime URL input.

## Run

```bash
cd /workspace/saleads-e2e
npm run playwright:install
SALEADS_START_URL="https://<current-environment>/login" npm test
```

Optional headed mode:

```bash
SALEADS_START_URL="https://<current-environment>/login" npm run test:headed
```

## Artifacts

Evidence is written to:

`artifacts/saleads_mi_negocio_full_test/<timestamp>/`

Including:

- checkpoint screenshots
- `final-report.json` with PASS/FAIL per required field
- captured URLs for legal pages
