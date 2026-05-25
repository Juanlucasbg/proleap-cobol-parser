# SaleADS Mi Negocio E2E

This folder contains a Playwright test for the workflow:

- Login with Google
- Open **Negocio > Mi Negocio**
- Validate **Agregar Negocio** modal
- Open **Administrar Negocios**
- Validate:
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
- Validate legal links:
  - Términos y Condiciones
  - Política de Privacidad
- Produce a final PASS/FAIL report

## Why this works for any environment

The test does not hardcode any SaleADS domain.  
Use `SALEADS_LOGIN_URL` to point to the login page of whichever environment you want (dev/staging/prod).

## Setup

From repository root:

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
cd e2e
SALEADS_LOGIN_URL="https://<your-saleads-environment>/login" npm test
```

Optional:

- `npm run test:headed` for headed mode
- `npm run test:list` to list tests
- `npm run test:report` to open HTML report

## Evidence output

Artifacts are saved in `e2e/test-results/`:

- Checkpoint screenshots
- `final-report.json` containing step-by-step PASS/FAIL and legal URLs
- Trace/video on failure
