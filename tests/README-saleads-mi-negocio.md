# SaleADS Mi Negocio full workflow test

This repository includes a Playwright end-to-end test for the full "Mi Negocio" workflow:

- Google login
- Sidebar > Negocio > Mi Negocio
- "Agregar Negocio" modal validation
- "Administrar Negocios" page validation
- "Información General", "Detalles de la Cuenta", and "Tus Negocios"
- Legal links ("Términos y Condiciones" and "Política de Privacidad")
- Final PASS/FAIL report by requested section

## 1) Install dependencies

```bash
npm install
npx playwright install --with-deps
```

## 2) Run the test

Use an environment variable for the login page of the current environment (dev/staging/prod) to avoid hardcoded domains:

```bash
SALEADS_LOGIN_URL="https://<current-environment>/login" npm run test:saleads-mi-negocio
```

Optional headed mode:

```bash
SALEADS_LOGIN_URL="https://<current-environment>/login" npm run test:saleads-mi-negocio:headed
```

## 3) Evidence and report outputs

- Checkpoint screenshots are saved under:
  - `saleads-evidence/<timestamp>/`
- Final report file:
  - `saleads-evidence/<timestamp>/final-report.json`

Report fields:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
