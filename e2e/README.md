# SaleADS Mi Negocio Full Workflow (Playwright)

This suite implements the `saleads_mi_negocio_full_test` flow end-to-end:

1. Login with Google
2. Open **Negocio > Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate **Información General**
6. Validate **Detalles de la Cuenta**
7. Validate **Tus Negocios**
8. Validate **Términos y Condiciones**
9. Validate **Política de Privacidad**
10. Emit final PASS/FAIL report

## Environment-agnostic behavior

- No hardcoded domain is used.
- Provide the login page URL for the current environment with:
  - `SALEADS_LOGIN_URL`, or
  - `BASE_URL`

## Setup

```bash
cd e2e
npm install
npx playwright install
```

## Run

```bash
cd e2e
SALEADS_LOGIN_URL="https://<current-env>/login" npm run test:mi-negocio
```

Optional:

- Run headed mode: `HEADLESS=false npm run test:mi-negocio`

## Evidence and reports

Generated artifacts are saved under:

- `e2e/evidence/screenshots/` (checkpoint screenshots)
- `e2e/evidence/saleads-mi-negocio-final-report.json` (PASS/FAIL summary + legal URLs)

The final report includes these fields:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
