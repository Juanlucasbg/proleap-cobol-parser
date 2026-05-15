# SaleADS Mi Negocio full workflow test

This package contains an environment-agnostic Playwright E2E test named
`saleads_mi_negocio_full_test` that validates the complete Mi Negocio workflow
after Google login.

## What it validates

The test validates and reports PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad

It also captures screenshots at key checkpoints and stores final legal-page URLs.

## Environment requirements

- A reachable SaleADS login URL from the target environment.
- Google sign-in access for `juanlucasbarbiergarzon@gmail.com` (if account chooser appears).

## Configuration

Set one of these variables to the login page URL of the active environment:

- `SALEADS_LOGIN_URL` (preferred)
- `SALEADS_START_URL`
- `BASE_URL`
- `APP_URL`

No domain is hardcoded in the test.

## Run

From this directory:

```bash
npm install
npx playwright install --with-deps chromium
npm test
```

Or run headed:

```bash
HEADLESS=false npm run test:headed
```

## Evidence and report output

Playwright artifacts are produced under:

- `test-results/` (screenshots, trace/video-on-failure, JSON report attachment)
- `playwright-report/` (HTML report)

The test writes a final JSON artifact named:

- `saleads-mi-negocio-final-report.json`
