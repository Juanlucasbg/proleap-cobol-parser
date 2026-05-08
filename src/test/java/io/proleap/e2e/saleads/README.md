# SaleADS Mi Negocio workflow test

This directory contains `SaleadsMiNegocioWorkflowTest`, a Selenium/JUnit test that validates the full **Mi Negocio** workflow requested in `saleads_mi_negocio_full_test`.

## What it validates

The test executes and reports PASS/FAIL for:

1. Login
2. Mi Negocio menu
3. Agregar Negocio modal
4. Administrar Negocios view
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones
9. Política de Privacidad

It also captures screenshots at important checkpoints and writes a final report with legal page URLs.

## Required environment variables

- `SALEADS_LOGIN_URL` (recommended): login page URL for the current environment (dev/staging/prod).
  - No domain is hardcoded in the test.
- `SELENIUM_REMOTE_URL` (optional): Selenium Grid URL if running remotely.
- `SALEADS_HEADLESS` (optional, default `true`): set `false` to run headed.
- `SALEADS_TIMEOUT_SECONDS` (optional, default `30`): explicit wait timeout.

## Running only this test

```bash
mvn -Dtest=io.proleap.e2e.saleads.SaleadsMiNegocioWorkflowTest test
```

## Evidence output

On each run, evidence is stored in:

`target/saleads-mi-negocio-evidence/<timestamp>/`

Including:

- Step screenshots (`*.png`)
- `final-report.txt` with PASS/FAIL summary and captured legal URLs
