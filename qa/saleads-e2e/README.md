# SaleADS E2E - Mi Negocio Full Workflow

This folder contains a Playwright end-to-end test that validates the complete **Mi Negocio** workflow requested in `saleads_mi_negocio_full_test`.

## Covered flow

1. Login with Google (including account selector when present).
2. Open `Negocio` -> `Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate:
   - `Información General`
   - `Detalles de la Cuenta`
   - `Tus Negocios`
6. Validate legal links:
   - `Términos y Condiciones`
   - `Política de Privacidad`
7. Generate final PASS/FAIL report per requested section.

The test is designed to be environment-agnostic:
- It does **not** hardcode any SaleADS domain.
- It can run on the already-open login page (default behavior).
- Or navigate to any environment login page via `SALEADS_LOGIN_URL`.

## Installation

From this directory:

```bash
npm install
npm run install:browsers
```

## Run

### Default (assumes the session starts on SaleADS login page)

```bash
npm test
```

### Explicit environment URL

```bash
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm test
```

### Override Google account selector target

```bash
GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" npm test
```

## Artifacts

Generated under `artifacts/`:

- `screenshots/` checkpoint screenshots:
  - dashboard loaded
  - Mi Negocio expanded menu
  - Agregar Negocio modal
  - Administrar Negocios page (full)
  - Términos y Condiciones page
  - Política de Privacidad page
- `saleads_mi_negocio_full_report.json` final PASS/FAIL summary and legal URLs.
- `playwright-report.json` and `html-report/` from Playwright reporters.

## Test file

- `tests/saleads_mi_negocio_full_test.spec.js`
