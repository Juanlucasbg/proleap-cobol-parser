# SaleADS Mi Negocio Full Workflow Test

This folder contains a Playwright end-to-end test that validates the complete "Mi Negocio" workflow requested in the automation task:

1. Login with Google.
2. Expand **Mi Negocio** menu.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate **Informacion General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Terminos y Condiciones**.
9. Validate **Politica de Privacidad**.
10. Emit a final PASS/FAIL report for each required area.

## Why it works across environments

- The test does not hardcode any SaleADS domain.
- Set the environment URL at runtime with `SALEADS_START_URL`.
- Selectors are primarily text-based and role-based to stay resilient across environments.

## Run

```bash
cd e2e/saleads
npm install
npx playwright install --with-deps
SALEADS_START_URL="https://<your-environment-login-url>" npm test
```

To run in headed mode:

```bash
SALEADS_START_URL="https://<your-environment-login-url>" npm run test:headed
```

## Evidence and report artifacts

Playwright stores run artifacts in `test-results/` including:

- Checkpoint screenshots (dashboard, menu, modal, administrar negocios, legal pages).
- `final-report.json` with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Informacion General
  - Detalles de la Cuenta
  - Tus Negocios
  - Terminos y Condiciones
  - Politica de Privacidad
