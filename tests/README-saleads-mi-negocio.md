# SaleADS Mi Negocio full workflow test

This repository now includes an end-to-end Playwright test:

- `tests/saleads-mi-negocio-full.spec.js`

## What it validates

The test covers the full requested workflow:

1. Login with Google.
2. Expand `Negocio` -> `Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Información General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Términos y Condiciones` (including new tab handling).
9. Validate `Política de Privacidad` (including new tab handling).
10. Produce a PASS/FAIL report with evidence.

## Environment independence

No domain is hardcoded. Use one of these variables when needed:

- `SALEADS_START_URL` (preferred)
- `BASE_URL` (fallback)

If the browser session already opens directly on login, you can run without a URL variable.

Optional:

- `SALEADS_EXPECTED_USER_NAME` to validate an exact user name in `Información General`.
- `HEADLESS=false` to run headed.

## Run

```bash
npm run e2e:install-browsers
SALEADS_START_URL="https://your-environment-login-page" npm run e2e:test
```

Headed mode:

```bash
SALEADS_START_URL="https://your-environment-login-page" HEADLESS=false npm run e2e:test:headed
```

## Evidence and report output

Artifacts are written under:

- `test-results/saleads-mi-negocio/<timestamp>/`

This includes:

- Checkpoint screenshots.
- `final-report.json` with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
