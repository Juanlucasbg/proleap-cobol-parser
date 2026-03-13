# SaleADS UI Tests

This folder contains environment-agnostic Playwright tests for SaleADS workflows.

## Test included

- `saleads_mi_negocio_full_test`

## Setup

```bash
cd ui-tests
npm install
npx playwright install --with-deps chromium
```

## Run

If your runner already opens the SaleADS login page, you can execute directly.

```bash
cd ui-tests
npm test -- --grep "saleads_mi_negocio_full_test"
```

If you need the test to navigate to login first, provide the login URL of your current environment:

```bash
cd ui-tests
SALEADS_LOGIN_URL="https://your-env.example/login" npm test -- --grep "saleads_mi_negocio_full_test"
```

Optional:

- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)

## Evidence and report

The test captures screenshots at required checkpoints and writes a structured final report as:

- `test-results/.../saleads-mi-negocio-final-report.json`

The report contains PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
