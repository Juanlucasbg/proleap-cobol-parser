# SaleADS Mi Negocio Full Workflow Test

This package contains an end-to-end Playwright test named `saleads_mi_negocio_full_test` that validates the full **Mi Negocio** flow after Google login.

## What it validates

The test performs and validates all requested checkpoints:

1. Login with Google and dashboard/sidebar visibility.
2. Open **Negocio > Mi Negocio** and verify submenu options.
3. Open and validate **Agregar Negocio** modal.
4. Open **Administrar Negocios** and validate account sections.
5. Validate **Información General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Términos y Condiciones** (same-tab or new-tab handling).
9. Validate **Política de Privacidad** (same-tab or new-tab handling).
10. Generate final PASS/FAIL report per required field.

## Environment-agnostic behavior

- No SaleADS domain is hardcoded.
- If the browser starts at `about:blank`, set `SALEADS_LOGIN_URL` to the current environment login page.
- Google account selection uses text matching and defaults to:
  - `juanlucasbarbiergarzon@gmail.com`
- You can override it with `SALEADS_GOOGLE_ACCOUNT_EMAIL`.

## Install and run

```bash
npm install
npm run install:browsers
SALEADS_LOGIN_URL="https://<your-environment>/login" npm test
```

If your runner already opens the login page before test start, `SALEADS_LOGIN_URL` is optional.

## Output artifacts

Artifacts are saved under:

```text
artifacts/saleads-mi-negocio/
```

Including:

- Screenshots for key checkpoints.
- `final-report.json` with PASS/FAIL by field and captured legal URLs.
