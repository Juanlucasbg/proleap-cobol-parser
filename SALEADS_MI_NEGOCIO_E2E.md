# SaleADS Mi Negocio Full Workflow Test

This repository now includes an end-to-end test at:

`src/test/java/io/proleap/saleads/e2e/SaleadsMiNegocioFullWorkflowTest.java`

## Purpose

Automates the following flow for any SaleADS environment:

1. Login with Google.
2. Open `Negocio -> Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open and validate `Administrar Negocios`.
5. Validate:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
6. Open and validate legal pages:
   - Terminos y Condiciones
   - Politica de Privacidad
7. Produce a final PASS/FAIL report per required field.

It also captures screenshots at key checkpoints and stores legal final URLs in the report.

## Execution

The test is intentionally gated so it does not run in normal CI by default.

Set environment variables:

- `SALEADS_RUN_E2E=true` (required to run)
- `SALEADS_LOGIN_URL=<login page for current environment>` (required, environment-agnostic)
- `SALEADS_HEADLESS=false` (optional, defaults to headless mode)
- `SALEADS_EXPECTED_USER_NAME=<expected user full name>` (optional, improves strict validation)
- `SALEADS_EXPECTED_USER_EMAIL=<expected user email>` (optional, defaults to `juanlucasbarbiergarzon@gmail.com`)

Run:

```bash
mvn -Dtest=io.proleap.saleads.e2e.SaleadsMiNegocioFullWorkflowTest test
```

## Artifacts

Generated under:

`target/saleads-e2e-artifacts/<timestamp>/`

Contains:

- checkpoint screenshots (`*.png`)
- `final-report.txt` with PASS/FAIL summary and legal URLs
