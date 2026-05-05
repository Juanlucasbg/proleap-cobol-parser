# SaleADS Mi Negocio Full Workflow Test

Playwright E2E test for:

- Google login
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios account view validation
- Información General / Detalles de la Cuenta / Tus Negocios checks
- Términos y Condiciones and Política de Privacidad navigation checks
- Step-by-step PASS/FAIL final report

## Environment-agnostic behavior

The test does not hardcode any SaleADS domain. It supports:

1. Starting from the already-open login page (as requested), or
2. Opening a provided URL via environment variable.

Supported variables:

- `SALEADS_LOGIN_URL` (preferred when login has a dedicated route)
- `SALEADS_BASE_URL` (fallback)

If both are missing, the test expects the browser context to already be on the login page.

## Install

```bash
cd automation/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
```

## Run

### Headless

```bash
npm test
```

### Headed

```bash
npm run test:headed
```

### With explicit URL

```bash
SALEADS_LOGIN_URL="https://your-environment.example.com/login" npm run test:headed
```

## Evidence and report outputs

Playwright stores artifacts in the default `test-results` and `playwright-report` directories.

The spec captures key screenshots:

- Dashboard loaded
- Mi Negocio expanded menu
- Agregar Negocio modal
- Administrar Negocios page (full)
- Términos y Condiciones page
- Política de Privacidad page

A final JSON report is attached to the test output as:

- `saleads_mi_negocio_final_report.json`

It includes PASS/FAIL status and details for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
