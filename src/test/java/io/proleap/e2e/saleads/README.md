# SaleADS Mi Negocio E2E

This folder contains an opt-in Selenium E2E test:

- `SaleadsMiNegocioFullWorkflowTest`

## Purpose

Validates the complete `Mi Negocio` workflow:

1. Login with Google
2. Open `Mi Negocio` menu
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Información General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Términos y Condiciones` (including tab handling and final URL capture)
9. Validate `Política de Privacidad` (including tab handling and final URL capture)

The test captures screenshots at important checkpoints and prints a final PASS/FAIL matrix.

## Configuration

Set configuration through environment variables or system properties (same key names):

- `SALEADS_E2E_ENABLED=true` (required)
- `SALEADS_LOGIN_URL=<current environment login URL>` (required)
- `SALEADS_GOOGLE_ACCOUNT=juanlucasbarbiergarzon@gmail.com` (optional, default shown)
- `SALEADS_EXPECTED_USER_NAME=<expected display name>` (optional)
- `SALEADS_HEADLESS=true|false` (optional, default `true`)
- `SALEADS_TIMEOUT_SECONDS=30` (optional)
- `SALEADS_SCREENSHOT_DIR=target/saleads-screenshots` (optional)

## Run

```bash
SALEADS_E2E_ENABLED=true \
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" \
mvn -Dtest=SaleadsMiNegocioFullWorkflowTest test
```

Screenshots are saved under `target/saleads-screenshots` by default.
