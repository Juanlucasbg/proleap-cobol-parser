# SaleADS Mi Negocio Full Test

This folder contains an environment-agnostic Playwright E2E test for the workflow:

1. Login with Google
2. Open **Negocio > Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate account sections
6. Validate legal links (**Términos y Condiciones** and **Política de Privacidad**)
7. Produce a final PASS/FAIL report by validation step

## Environment requirements

- Node.js 18+
- Browser binaries installed for Playwright:

```bash
npx playwright install --with-deps chromium
```

## Run

From this folder:

```bash
npm install
SALEADS_LOGIN_URL="https://<current-env-login-url>" npm run test:mi-negocio
```

Notes:

- No fixed domain is used in the test.
- If `SALEADS_LOGIN_URL` is not set, the test assumes navigation is handled externally.
- Artifacts and screenshots are generated in `artifacts/<timestamp>/`.

## Output

Each run produces:

- Checkpoint screenshots (dashboard, expanded menu, modal, account view, legal pages)
- `final-report.json` with PASS/FAIL status for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
