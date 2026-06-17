# SaleADS Mi Negocio Full Workflow Test

This Playwright suite automates the full `saleads_mi_negocio_full_test` workflow:

1. Login with Google.
2. Open and validate **Mi Negocio** menu.
3. Open and validate **Agregar Negocio** modal.
4. Open and validate **Administrar Negocios** view.
5. Validate **Informacion General** section.
6. Validate **Detalles de la Cuenta** section.
7. Validate **Tus Negocios** section.
8. Validate **Terminos y Condiciones** (new tab or same tab).
9. Validate **Politica de Privacidad** (new tab or same tab).
10. Emit final PASS/FAIL report in JSON.

## Environment-agnostic behavior

- The test does **not** hardcode any SaleADS domain.
- It runs against the environment URL passed in `SALEADS_URL`.
- Google account selection defaults to `juanlucasbarbiergarzon@gmail.com` and can be overridden.

## Required variables

- `SALEADS_URL`: Login page URL for the target environment (dev/staging/prod).

Optional:

- `SALEADS_GOOGLE_ACCOUNT`: Google account to choose if account selector appears.
- `SALEADS_SCREENSHOT_DIR`: Where checkpoint screenshots and final report are written.
- `HEADLESS`: Set to `false` for headed mode.

## Install

```bash
npm install
npx playwright install --with-deps chromium
```

## Run

Headless:

```bash
SALEADS_URL="https://<current-env-login-url>" npm run test:saleads
```

Headed:

```bash
SALEADS_URL="https://<current-env-login-url>" HEADLESS=false npm run test:saleads:headed
```

## Evidence generated

- Checkpoint screenshots:
  - Dashboard loaded
  - Mi Negocio menu expanded
  - Agregar Negocio modal
  - Administrar Negocios page
  - Terminos y Condiciones page
  - Politica de Privacidad page
- Final report:
  - `<screenshotsDir>/final-report.json`
  - Includes PASS/FAIL per requested field and final legal-page URLs.
