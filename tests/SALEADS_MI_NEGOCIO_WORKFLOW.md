# SaleADS Mi Negocio Full Workflow Test

This repository now includes a Playwright test that validates the full Mi Negocio workflow:

- Login with Google
- Sidebar navigation checks
- Mi Negocio submenu checks
- Agregar Negocio modal checks
- Administrar Negocios page checks
- Informacion General, Detalles de la Cuenta, and Tus Negocios checks
- Terminos y Condiciones and Politica de Privacidad link checks (same-tab or new-tab)
- PASS/FAIL final report generation

## 1) Install dependencies

```bash
npm install
npm run playwright:install
```

## 2) Run the test against any environment

Set the login page URL of the current environment (dev/staging/prod) at runtime:

```bash
SALEADS_START_URL="https://<current-environment-login-url>" npm run test:saleads:mi-negocio
```

No domain is hardcoded in the test.

## 3) Evidence output

The test saves screenshots and a final report under:

```text
evidence/saleads_mi_negocio_full_test/
```

Main artifact:

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
