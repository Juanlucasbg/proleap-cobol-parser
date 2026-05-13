# SaleADS Mi Negocio full workflow test

This directory contains an end-to-end Selenium test for the SaleADS "Mi Negocio" module workflow:

- `SaleadsMiNegocioFullWorkflowTest`

## Execution

The test is opt-in and will be skipped unless explicitly enabled.

```bash
SALEADS_E2E_ENABLED=true \
SALEADS_START_URL="https://<current-saleads-environment>/login" \
mvn -Dtest=io.proleap.cobol.e2e.SaleadsMiNegocioFullWorkflowTest test
```

Optional:

- `SALEADS_E2E_HEADLESS=false` to run with visible browser
- JVM properties can also be used:
  - `-Dsaleads.e2e.enabled=true`
  - `-Dsaleads.start.url=...`
  - `-Dsaleads.e2e.headless=false`

## Evidence output

On each run, evidence is written under:

- `target/saleads-evidence/<timestamp>/`

Artifacts include:

- Checkpoint screenshots (`01-...png`, `02-...png`, etc.)
- Failure screenshots (`failure-...png`)
- Final status report (`final-report.txt`) with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Informacion General
  - Detalles de la Cuenta
  - Tus Negocios
  - Terminos y Condiciones
  - Politica de Privacidad
