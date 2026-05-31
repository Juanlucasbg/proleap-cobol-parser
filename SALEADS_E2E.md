# SaleADS Mi Negocio E2E Test

This repository now includes an opt-in Selenium test that validates the full SaleADS "Mi Negocio" workflow:

- Google login
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Informacion General / Detalles de la Cuenta / Tus Negocios checks
- Terminos y Condiciones + Politica de Privacidad checks (same-tab or new-tab)
- Checkpoint screenshots and final PASS/FAIL report fields

## Test class

`src/test/java/io/proleap/cobol/e2e/SaleadsMiNegocioWorkflowTest.java`

## Required environment variables

- `SALEADS_E2E_ENABLED=true`
- `SALEADS_LOGIN_URL=<login page for the current environment>`

## Optional environment variables

- `SALEADS_GOOGLE_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_HEADLESS=true|false` (default: `false`)
- `SALEADS_CHROME_USER_DATA_DIR=<path>` (for persisted Google/session login)
- `SALEADS_CHROME_PROFILE_DIR=<profile>` (e.g. `Default`)

## Run only this workflow test

```bash
mvn -Dtest=io.proleap.cobol.e2e.SaleadsMiNegocioWorkflowTest test
```

## Evidence output

Screenshots are written to:

`target/saleads-screenshots/<timestamp>/`

The test also prints:

- PASS/FAIL report for each required field
- Final URL for Terminos y Condiciones
- Final URL for Politica de Privacidad
