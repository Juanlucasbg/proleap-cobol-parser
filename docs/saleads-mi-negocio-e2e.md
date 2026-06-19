# SaleADS Mi Negocio E2E workflow test

This repository includes an end-to-end automation test for the SaleADS "Mi Negocio" workflow:

- Test class: `io.proleap.e2e.saleads.SaleadsMiNegocioWorkflowTest`
- Source file: `src/test/java/io/proleap/e2e/saleads/SaleadsMiNegocioWorkflowTest.java`

## What it validates

The flow follows the requested sequence and validates:

1. Login with Google and left sidebar visibility.
2. "Mi Negocio" menu expansion.
3. "Agregar Negocio" modal content.
4. "Administrar Negocios" account page sections.
5. "Información General".
6. "Detalles de la Cuenta".
7. "Tus Negocios".
8. "Términos y Condiciones" (including new-tab handling).
9. "Política de Privacidad" (including new-tab handling).

The test captures screenshots at the required checkpoints and writes a final PASS/FAIL report.

## Environment-agnostic behavior

- No SaleADS domain is hardcoded.
- The test uses `SALEADS_START_URL` to open whichever environment login page is needed (dev/staging/prod).

## Required environment variables

- `SALEADS_START_URL`: Login page URL of the target SaleADS environment.

Optional:

- `SALEADS_HEADLESS` (default: `true`)
- `SALEADS_EXPECTED_USER_NAME` (if you want strict username validation)
- `SALEADS_EXPECTED_USER_EMAIL` (defaults to `juanlucasbarbiergarzon@gmail.com`)

## Run

```bash
SALEADS_START_URL="https://<current-env-login-url>" \
mvn -Dtest=SaleadsMiNegocioWorkflowTest test
```

## Artifacts

For each run, artifacts are stored under:

`target/saleads-mi-negocio/<UTC timestamp>/`

Generated files include:

- `saleads-mi-negocio-report.json`
- `saleads-mi-negocio-report.md`
- checkpoint screenshots (`*.png`)
