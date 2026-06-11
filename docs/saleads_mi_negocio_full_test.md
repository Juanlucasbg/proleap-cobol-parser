# SaleADS Mi Negocio Full Test

This automation implements the `saleads_mi_negocio_full_test` workflow end-to-end:

1. Login with Google.
2. Open **Negocio > Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate **Información General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Términos y Condiciones** (including tab handling and URL capture).
9. Validate **Política de Privacidad** (including tab handling and URL capture).
10. Emit final PASS/FAIL report per requested field.

The script captures screenshots at each required checkpoint and writes a final JSON report with legal URLs.

## Run

```bash
npm install
npx playwright install chromium
npm run saleads:mi-negocio
```

## Configuration

No domain is hardcoded. Provide one of the following:

- `SALEADS_BASE_URL` (or `SALEADS_URL`) to open the login page dynamically.
- `SALEADS_CDP_URL` to connect to an existing browser session that is already on the login page.

Optional:

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_HEADLESS` (`false` to run headed)
- `SALEADS_TIMEOUT_MS` (default: `20000`)
- `SALEADS_ARTIFACT_DIR` (default: `artifacts/saleads-mi-negocio`)

## Output

Artifacts are stored under:

`artifacts/saleads-mi-negocio/<timestamp>/`

- Screenshots (`*.png`)
- `final-report.json` with PASS/FAIL fields:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
