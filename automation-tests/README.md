# SaleADS UI Automation

This folder contains the `saleads_mi_negocio_full_test` workflow automation using Playwright.

## What this test validates

The script performs the full Mi Negocio workflow end-to-end:

1. Login with Google (including account chooser handling).
2. Open sidebar `Mi Negocio` menu and validate submenu options.
3. Open and validate `Agregar Negocio` modal.
4. Open `Administrar Negocios` and validate all required sections.
5. Validate `Informacion General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Terminos y Condiciones` (same tab or new tab).
9. Validate `Politica de Privacidad` (same tab or new tab).
10. Print and persist a PASS/FAIL final report per requested field.

Screenshots are captured at important checkpoints and saved with the report.

## Environment-agnostic usage

No SaleADS domain is hardcoded. Provide the login page URL of the current environment at runtime:

```bash
cd automation-tests
SALEADS_LOGIN_URL="https://<your-saleads-login-url>" npm run test:saleads:mi-negocio
```

Optional environment variables:

- `SALEADS_BASE_URL` (fallback if `SALEADS_LOGIN_URL` is not provided)
- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS=true|false` (default: `false`)

## Output artifacts

For each run, artifacts are written to:

`automation-tests/artifacts/saleads_mi_negocio_full_test_<timestamp>/`

Contents include:

- `screenshots/*.png`
- `final-report.json` (PASS/FAIL by step + legal final URLs)
