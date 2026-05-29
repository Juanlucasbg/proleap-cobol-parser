# SaleADS Mi Negocio - Full Workflow E2E

This Playwright suite automates the full workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google
2. Open **Negocio > Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate:
   - Información General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Términos y Condiciones
   - Política de Privacidad
7. Generate final PASS/FAIL report per required field

It is environment-agnostic and does not hardcode any SaleADS domain.

## Setup

```bash
cd /workspace/e2e/saleads
npm install
npx playwright install --with-deps chromium
```

## Configuration

Create `.env` (or export vars in your shell):

```bash
SALEADS_START_URL=https://<current-environment>/login
SALEADS_ACCOUNT_EMAIL=juanlucasbarbiergarzon@gmail.com
PW_HEADLESS=true
```

- `SALEADS_START_URL` is optional if your runtime already opens the login page before the test starts.
- `SALEADS_ACCOUNT_EMAIL` defaults to `juanlucasbarbiergarzon@gmail.com`.

## Run

```bash
cd /workspace/e2e/saleads
npm test
```

Useful variants:

```bash
npm run test:list
npm run test:headed
```

## Artifacts and evidence

Generated under:

`artifacts/saleads_mi_negocio_full_test/<timestamp>/`

- `screenshots/*.png` (dashboard, menu, modal, account page, legal pages)
- `final-report.json` (step-by-step PASS/FAIL, validations, legal final URLs)

Latest report pointer:

`artifacts/saleads_mi_negocio_full_test/latest-report.json`
