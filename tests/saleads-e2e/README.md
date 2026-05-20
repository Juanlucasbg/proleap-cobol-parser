# SaleADS Mi Negocio full workflow test

This folder contains a standalone Playwright E2E test:

- `saleads_mi_negocio_full_test`

It validates the full requested flow:

1. Login with Google.
2. Open and validate **Mi Negocio** menu.
3. Open and validate **Agregar Negocio** modal.
4. Open and validate **Administrar Negocios** view.
5. Validate **Información General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Términos y Condiciones** (including new-tab support).
9. Validate **Política de Privacidad** (including new-tab support).
10. Generate final PASS/FAIL report.

## Run

```bash
cd tests/saleads-e2e
npm install
npx playwright install --with-deps
npm test
```

## Environment configuration

No environment URL is hardcoded.

You can provide the login page for the current environment with:

```bash
SALEADS_LOGIN_URL="https://<current-environment>/login" npm test
```

If `SALEADS_LOGIN_URL` is not set, the test uses the page provided by the runtime/session.

## Evidence generated

Important checkpoints are saved as screenshots in Playwright output artifacts:

- dashboard loaded
- Mi Negocio menu expanded
- Agregar Negocio modal
- Administrar Negocios page
- Términos y Condiciones page
- Política de Privacidad page

The final JSON report includes PASS/FAIL per requested field and final legal URLs.
