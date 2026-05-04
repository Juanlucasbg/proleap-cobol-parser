# SaleADS Mi Negocio E2E Workflow

This repository now includes an opt-in Selenium E2E test that validates the full "Mi Negocio" workflow described by automation task `saleads_mi_negocio_full_test`.

## Test class

- `src/test/java/io/proleap/cobol/e2e/SaleadsMiNegocioWorkflowTest.java`

## Why opt-in

This repository is mainly a Java parser project, so the SaleADS browser workflow test is guarded to avoid breaking normal CI.

The E2E test only runs when `SALEADS_E2E_ENABLED=true`.

## Environment variables

- `SALEADS_E2E_ENABLED` (required): set to `true` to run the workflow test.
- `SALEADS_START_URL` (required): login URL for the current SaleADS environment (dev/staging/prod).
- `SALEADS_GOOGLE_ACCOUNT_EMAIL` (optional): defaults to `juanlucasbarbiergarzon@gmail.com`.
- `SALEADS_HEADLESS` (optional): defaults to `true`.
- `SALEADS_TIMEOUT_SECONDS` (optional): defaults to `30`.

## Run command

```bash
SALEADS_E2E_ENABLED=true \
SALEADS_START_URL="https://<current-saleads-login>" \
SALEADS_GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" \
mvn -Dtest=io.proleap.cobol.e2e.SaleadsMiNegocioWorkflowTest test
```

## Evidence and report output

Artifacts are written to:

- `target/saleads-mi-negocio-e2e/<timestamp>/`

Including:

- Checkpoint screenshots
- `final-report.txt` with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad

The report also includes final captured URLs for legal pages.
