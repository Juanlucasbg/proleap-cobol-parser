# SaleADS Mi Negocio Full Workflow Test

This folder contains an isolated Playwright test named:

- `saleads_mi_negocio_full_test`

It validates the full workflow requested for SaleADS:

1. Login with Google.
2. Open `Negocio` -> `Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (same-tab or new-tab).
9. Validate `Política de Privacidad` (same-tab or new-tab).
10. Produce PASS/FAIL report for all fields.

## Why this is environment-agnostic

- No domain is hardcoded.
- Target environment is passed via `SALEADS_BASE_URL`.
- Selectors prioritize visible text (`Negocio`, `Agregar Negocio`, etc.).

## Run

From this directory:

```bash
npm install
npx playwright install chromium
SALEADS_BASE_URL="https://<your-saleads-environment-login-url>" npm run test:saleads:headed
```

## Optional environment variables

- `SALEADS_BASE_URL`: Login page URL of the active environment (dev/staging/prod).
- `SALEADS_GOOGLE_ACCOUNT_EMAIL`: Google account to choose in selector.
  - Default: `juanlucasbarbiergarzon@gmail.com`
- `SALEADS_ARTIFACTS_DIR`: Folder for generated evidence.
- `SALEADS_REPORT_PATH`: Custom report output path.

## Evidence produced

- Checkpoint screenshots under:
  - `artifacts/screenshots/`
- Final step report:
  - `artifacts/saleads_mi_negocio_full_test_report.json`

The report includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
