# SaleADS Mi Negocio full workflow test

This Playwright script automates the full "Mi Negocio" flow requested in `saleads_mi_negocio_full_test`.

## Run

```bash
npm run saleads:mi-negocio:test
```

## Environment variables

- `SALEADS_LOGIN_URL` (optional): Login URL for the current environment (dev/staging/prod).  
  The script does **not** hardcode any domain.
- `SALEADS_BASE_URL` (optional): Alias for `SALEADS_LOGIN_URL`.
- `SALEADS_CDP_URL` (optional): Connect to an already opened Chromium session via CDP (useful when the browser is already on the login page).
- `GOOGLE_ACCOUNT_EMAIL` (optional): Defaults to `juanlucasbarbiergarzon@gmail.com`.
- `HEADLESS` (optional): `true`/`false`, default `true`.
- `SALEADS_ARTIFACTS_DIR` (optional): Output directory for screenshots and report.

## Evidence and report

The script writes artifacts under:

```text
artifacts/saleads-mi-negocio/
```

Including:

- Checkpoint screenshots
- `final-report.json` with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
