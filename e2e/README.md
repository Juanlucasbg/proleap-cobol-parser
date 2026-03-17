# SaleADS E2E Automation

This folder contains environment-agnostic Playwright tests for SaleADS.ai flows.

## Implemented flow

- `tests/saleads.mi-negocio.full.spec.js`
  - Login with Google (then continue, not stop after login)
  - Open **Negocio > Mi Negocio**
  - Validate **Agregar Negocio** modal
  - Open **Administrar Negocios**
  - Validate:
    - Informacion General
    - Detalles de la Cuenta
    - Tus Negocios
    - Terminos y Condiciones
    - Politica de Privacidad
  - Capture screenshots at key checkpoints
  - Generate a JSON PASS/FAIL report for required fields

## Run

From this `e2e` directory:

```bash
npm run install:browsers
SALEADS_URL="https://<current-env-login-page>" npm run test:mi-negocio
```

You can also use:

```bash
SALEADS_URL="https://<current-env-login-page>" npm run test:headed
```

## Notes

- No fixed domain is hardcoded.
- Use `SALEADS_URL` (or `SALEADS_LOGIN_URL`) for the active environment (dev/staging/prod).
- The test attempts to select `juanlucasbarbiergarzon@gmail.com` when Google account chooser appears.
- Final report artifact: `mi-negocio-final-report.json` in Playwright test output.
