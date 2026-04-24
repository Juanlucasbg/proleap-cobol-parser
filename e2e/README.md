# SaleADS E2E Tests

This folder contains a standalone Playwright automation for:

- `saleads_mi_negocio_full_test`

The workflow validates:

1. Login with Google (and continue, not stop at login)
2. Sidebar Negocio -> Mi Negocio expansion
3. Agregar Negocio modal
4. Administrar Negocios view
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones (same tab or new tab)
9. Politica de Privacidad (same tab or new tab)
10. Final PASS/FAIL report

## Environment agnostic configuration

No specific domain is hardcoded.

Use one of:

- `SALEADS_LOGIN_URL`
- `SALEADS_BASE_URL`

Example:

```bash
cd /workspace/e2e
export SALEADS_LOGIN_URL="https://<current-environment-login-page>"
npm run pw:install
npm run test:saleads-mi-negocio
```

If the browser is already at the login page (as in manual/interactive runs), the test can also proceed without explicit navigation.

## Artifacts

- Screenshots: `e2e/artifacts/screenshots/`
- JSON final report: `e2e/artifacts/reports/`
- Playwright HTML report: `e2e/playwright-report/`

The JSON report includes:

- PASS/FAIL for each required report field:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Informacion General
  - Detalles de la Cuenta
  - Tus Negocios
  - Terminos y Condiciones
  - Politica de Privacidad
- Final URLs for legal links
- Whether each legal page opened in a new tab
