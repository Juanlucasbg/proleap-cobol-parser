# SaleADS Mi Negocio Full Workflow Test

This isolated Playwright suite validates the complete `saleads_mi_negocio_full_test` flow:

- Login with Google
- Navigate to `Mi Negocio`
- Validate `Agregar Negocio` modal
- Validate `Administrar Negocios` sections
- Validate legal links (`Términos y Condiciones`, `Política de Privacidad`)
- Produce PASS/FAIL report per requested field

## Environment-agnostic configuration

Set the login page URL with an environment variable (no hardcoded domain in code):

```bash
export SALEADS_LOGIN_URL="https://<current-env-login-page>"
```

Alternative key also supported:

```bash
export saleads_login_url="https://<current-env-login-page>"
```

## Install and run

```bash
cd automation/saleads-mi-negocio
npm install
npx playwright install chromium
npm test
```

## Artifacts

- Screenshots: `automation/saleads-mi-negocio/reports/screenshots/`
- Final JSON report: `automation/saleads-mi-negocio/reports/saleads-mi-negocio-full-test-report.json`
- Playwright HTML report: `automation/saleads-mi-negocio/playwright-report/`

The JSON report includes one PASS/FAIL status entry for each requested report field:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
