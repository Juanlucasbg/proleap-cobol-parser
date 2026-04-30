# SaleADS Mi Negocio E2E

This folder contains the `saleads_mi_negocio_full_test` Playwright workflow.

## Purpose

Validate the full **SaleADS Mi Negocio** journey (not only login):

1. Login with Google
2. Open Negocio → Mi Negocio
3. Validate "Agregar Negocio" modal
4. Open "Administrar Negocios"
5. Validate:
   - Información General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Términos y Condiciones
   - Política de Privacidad
7. Generate PASS/FAIL report + screenshots

## Environment-agnostic usage

No hardcoded domain is used in the test.

Provide the environment login URL through:

- `SALEADS_START_URL`, or
- `BASE_URL`

If neither variable is set, the test assumes the browser is already on the SaleADS login page, matching the original requirement.

## Run

Install dependencies once:

```bash
npm install
npx playwright install chromium
```

Run headless:

```bash
SALEADS_START_URL="https://<current-env-login-url>" npm run test:saleads
```

Run headed:

```bash
SALEADS_START_URL="https://<current-env-login-url>" npm run test:saleads:headed
```

## Artifacts

- Screenshots: `test-results/saleads-mi-negocio-screenshots/`
- Final report: `test-results/saleads-mi-negocio-report.json`

The JSON report contains:

- PASS/FAIL per required validation area
- Validation details
- Final URLs captured for legal pages
