# SaleADS Mi Negocio Full Workflow Test

This Playwright test automates the complete workflow requested for the **Mi Negocio** module:

1. Login with Google.
2. Expand and validate the **Mi Negocio** menu.
3. Validate the **Agregar Negocio** modal.
4. Open and validate **Administrar Negocios**.
5. Validate:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Terminos y Condiciones
   - Politica de Privacidad
7. Print a final PASS/FAIL report for all required fields.

## Requirements

- Node.js 20+
- Playwright browsers installed

## Setup

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install
```

## Run

Set the login page URL of the current environment (dev/staging/prod) through environment variables.
No domain is hardcoded in the test.

```bash
export SALEADS_LOGIN_URL="https://<your-saleads-environment>/login"
npm test
```

Optional:

- `HEADLESS=false npm test` to watch execution.
- `npm run test:headed` to force headed mode.
- `npm run test:ui` to use Playwright UI mode.

## Evidence

Important checkpoints are captured as screenshots under:

- `e2e/saleads-mi-negocio/artifacts/screenshots/`

The final report (PASS/FAIL + legal URLs) is printed in test output and attached to the Playwright test artifacts.
