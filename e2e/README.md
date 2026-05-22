# SaleADS Mi Negocio Full Workflow Test

This script automates the full Mi Negocio workflow requested in `saleads_mi_negocio_full_test`, including:

- Login with Google (or continue if already authenticated)
- Sidebar navigation validations (`Negocio` / `Mi Negocio`)
- `Agregar Negocio` modal validations
- `Administrar Negocios` page section validations
- Legal link validations for:
  - `Términos y Condiciones`
  - `Política de Privacidad`
- Screenshots at key checkpoints
- Final PASS/FAIL report per required field

## Run

```bash
npm install
npm run saleads:mi-negocio:test
```

## Environment variables

- `SALEADS_CDP_URL` (optional): CDP endpoint to attach to an already-open browser session/page.
  - Use this mode to satisfy the "browser is already on login page" requirement.
- `SALEADS_LOGIN_URL` (optional if CDP is set): Login URL for launch mode (no hardcoded domain in script).
- `SALEADS_GOOGLE_ACCOUNT` (optional): Defaults to `juanlucasbarbiergarzon@gmail.com`.
- `SALEADS_ARTIFACT_DIR` (optional): Where screenshots and report are written.
- `HEADLESS` (optional): set to `false` for headed mode.

## Outputs

- Screenshots and `report.json` are written to:
  - `artifacts/saleads-mi-negocio/<timestamp>/`

The JSON report includes:

- Step-by-step actions and validations
- Evidence paths (screenshots + legal URLs)
- Final summary with required report fields:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
