# SaleADS Mi Negocio full workflow test

This test automates the `saleads_mi_negocio_full_test` workflow using Playwright.

## Environment variables

- `SALEADS_LOGIN_URL` (or `SALEADS_URL`): Login URL for the current environment (dev/staging/prod).
- `SALEADS_GOOGLE_ACCOUNT` (optional): Google account email to select when Google account picker appears.
  - Default: `juanlucasbarbiergarzon@gmail.com`

## Run

```bash
npx playwright install --with-deps chromium
npm run test:saleads-mi-negocio
```

## Output

- Checkpoint screenshots are saved under:
  - `artifacts/saleads-mi-negocio/<timestamp>/`
- Final status report:
  - `artifacts/saleads-mi-negocio/<timestamp>/final-report.json`

The JSON report includes PASS/FAIL per required section:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
