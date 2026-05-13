# SaleADS Mi Negocio Full Test

This script validates the complete **Mi Negocio** workflow in any SaleADS.ai environment without hardcoding a specific domain.

## Install

```bash
cd e2e
npm install
npx playwright install chromium
```

## Run

Set the runtime URL from the target environment:

```bash
cd e2e
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm run test:saleads-mi-negocio
```

Optional runtime flags:

- `HEADLESS=false` to run with visible browser UI.
- `SALEADS_URL` or `BASE_URL` can be used instead of `SALEADS_LOGIN_URL`.

## Output

Each execution creates artifacts under:

```text
e2e/artifacts/saleads_mi_negocio_full_test/<timestamp>/
```

Artifacts include:

- Checkpoint screenshots (dashboard, expanded menu, modal, account page, legal pages)
- `final_report.json` with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
