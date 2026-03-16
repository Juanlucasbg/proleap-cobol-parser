# SaleADS Mi Negocio E2E Test

This repository now includes an end-to-end test for the requested SaleADS flow:

- Login with Google
- Expand **Mi Negocio**
- Validate **Agregar Negocio** modal
- Open **Administrar Negocios**
- Validate:
  - Informacion General
  - Detalles de la Cuenta
  - Tus Negocios
  - Terminos y Condiciones
  - Politica de Privacidad
- Capture screenshots and generate a final PASS/FAIL report by section

## Test class

`src/test/java/io/proleap/cobol/e2e/SaleadsMiNegocioFullWorkflowTest.java`

## Execution

The test is disabled by default and only runs when explicitly enabled.

Required:

- `SALEADS_E2E_ENABLED=true` (or `-Dsaleads.e2e.enabled=true`)
- `SALEADS_LOGIN_URL=<environment-login-url>` (or `-Dsaleads.login.url=...`)

Optional:

- `SALEADS_BROWSER=chrome|firefox` (default: `chrome`)
- `SALEADS_HEADLESS=true|false` (default: `false`)
- `SALEADS_TIMEOUT_SECONDS=<seconds>` (default: `30`)

Example:

```bash
SALEADS_E2E_ENABLED=true \
SALEADS_LOGIN_URL="https://<your-saleads-environment>/login" \
mvn -Dtest=io.proleap.cobol.e2e.SaleadsMiNegocioFullWorkflowTest test
```

## Evidence output

Artifacts are written under:

`target/saleads-evidence/<timestamp-uuid>/`

This directory contains:

- screenshots (`.png`)
- final JSON report:
  - `saleads_mi_negocio_full_test_report.json`
