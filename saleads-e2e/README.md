# SaleADS Mi Negocio Full Workflow Test

This folder contains an environment-agnostic Playwright test for the full **Mi Negocio** workflow:

- Google login
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios sections
- Información General / Detalles / Tus Negocios
- Términos y Condiciones / Política de Privacidad (same tab or new tab)
- Final PASS/FAIL report per requested field

## 1) Install dependencies

```bash
cd saleads-e2e
npm install
npx playwright install --with-deps chromium
```

## 2) Run test in any environment

Do not hardcode domains in code. Provide the current environment login URL at runtime:

```bash
SALEADS_LOGIN_URL="https://<current-env-login-url>" npm test
```

Accepted variables:

- `SALEADS_LOGIN_URL` (preferred)
- `SALEADS_BASE_URL`
- `BASE_URL`

## 3) Outputs

The test stores evidence in:

- `saleads-e2e/artifacts/<timestamp>/`
  - checkpoint screenshots
  - `final-report.json` with PASS/FAIL fields:
    - Login
    - Mi Negocio menu
    - Agregar Negocio modal
    - Administrar Negocios view
    - Información General
    - Detalles de la Cuenta
    - Tus Negocios
    - Términos y Condiciones
    - Política de Privacidad

If a legal link opens a new tab, the test validates it, captures evidence, records final URL, and returns to the app tab.
