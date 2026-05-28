# SaleADS Mi Negocio Full Workflow Test

This folder contains an end-to-end Playwright script for the workflow:

`saleads_mi_negocio_full_test`

The script validates:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal
4. Administrar Negocios view
5. Información General section
6. Detalles de la Cuenta section
7. Tus Negocios section
8. Términos y Condiciones page
9. Política de Privacidad page
10. Final PASS/FAIL report generation

## Why this works across environments

- No domain is hardcoded.
- The login page URL is provided at runtime with `SALEADS_LOGIN_URL` (or `SALEADS_URL`).
- Selectors prioritize visible text in Spanish/English labels where applicable.

## Run

From repository root:

```bash
cd e2e
npx playwright install chromium
SALEADS_LOGIN_URL="https://<your-saleads-environment>/login" npm run saleads:mi-negocio
```

Optional:

- `HEADLESS=false` to run headed mode.
- `SALEADS_ARTIFACTS_DIR=/path/to/output` to override artifact output location.

## Output artifacts

The test writes:

- Checkpoint screenshots (dashboard, menu, modal, account view, legal pages)
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
- Captured final URLs for legal pages
