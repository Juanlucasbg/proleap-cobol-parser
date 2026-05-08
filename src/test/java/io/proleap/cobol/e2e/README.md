# SaleADS Mi Negocio Full Workflow Test

This folder contains `SaleadsMiNegocioFullTest`, a Selenium/JUnit test that validates the full Mi Negocio flow requested in `saleads_mi_negocio_full_test`.

## What the test validates

1. Login with Google and dashboard/sidebar visibility
2. Mi Negocio menu expansion
3. Agregar Negocio modal content
4. Administrar Negocios page sections
5. Informacion General data
6. Detalles de la Cuenta fields
7. Tus Negocios block
8. Terminos y Condiciones legal page (+ URL capture)
9. Politica de Privacidad legal page (+ URL capture)

The test prints a final PASS/FAIL report for these nine report fields and saves screenshots to:

`target/saleads-evidence/<timestamp>/`

## Runtime configuration

- `SALEADS_LOGIN_URL` (required): login page URL for the current environment (dev/staging/prod)
- `SALEADS_HEADLESS` (optional, default `true`)
- `SALEADS_WAIT_SECONDS` (optional, default `30`)
- `SALEADS_CHROME_BINARY` (optional)

## Run command

```bash
mvn -Dtest=io.proleap.cobol.e2e.SaleadsMiNegocioFullTest test
```

Example:

```bash
SALEADS_LOGIN_URL="https://<current-env>/login" SALEADS_HEADLESS=false mvn -Dtest=io.proleap.cobol.e2e.SaleadsMiNegocioFullTest test
```
