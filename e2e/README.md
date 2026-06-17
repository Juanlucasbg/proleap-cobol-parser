# SaleADS.ai E2E tests

This folder contains an end-to-end test for the **Mi Negocio** workflow:

- `tests/saleads-mi-negocio-full-test.spec.js`

## What it validates

The test automates the complete flow requested in `saleads_mi_negocio_full_test`:

1. Login with Google.
2. Open sidebar menu **Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate **Información General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Términos y Condiciones** (same tab or new tab).
9. Validate **Política de Privacidad** (same tab or new tab).
10. Produce PASS/FAIL final report attachment.

The test takes screenshots at key checkpoints and writes a JSON report attachment (`final-report.json`) with:

- PASS/FAIL by step
- final URLs for legal pages
- error details (if any)

## Environment-agnostic behavior

- No SaleADS domain is hardcoded.
- If the browser starts at `about:blank`, set `SALEADS_URL` to the login page of the current environment.
- If the runner/session is already on the login page, the test uses the current page.

## Run

Install dependencies and browser:

```bash
cd /workspace/e2e
npm install
npm run install:browsers
```

Run test (headless):

```bash
SALEADS_URL="https://<current-env-login-url>" npm run test:mi-negocio
```

Run in headed mode:

```bash
SALEADS_URL="https://<current-env-login-url>" npm run test:mi-negocio -- --headed
```

## Notes

- Google auth flows can vary by environment and account session state.
- The test attempts account selection for `juanlucasbarbiergarzon@gmail.com` when the selector appears.
