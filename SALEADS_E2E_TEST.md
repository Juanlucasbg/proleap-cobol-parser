# SaleADS.ai - Mi Negocio Full Workflow Test

This repository includes an end-to-end UI test for the workflow:

- Login with Google
- Open **Mi Negocio**
- Validate **Agregar Negocio** modal
- Open **Administrar Negocios**
- Validate account sections
- Validate legal links (**Terminos y Condiciones** and **Politica de Privacidad**)

## Test class

`src/test/java/io/proleap/cobol/e2e/SaleadsMiNegocioWorkflowTest.java`

## Environment variables

Required:

- `SALEADS_LOGIN_URL`: Login page URL for the target environment (dev/staging/prod).

Optional:

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_EXPECTED_USER_NAME` (recommended for strict name validation)
- `SALEADS_HEADLESS` (default: `true`)
- `SALEADS_TIMEOUT_MS` (default: `30000`)
- `SALEADS_SCREENSHOT_DIR` (default: `target/saleads-evidence`)

## Run command

```bash
SALEADS_LOGIN_URL="https://<current-env>/login" mvn -Dtest=SaleadsMiNegocioWorkflowTest test
```

## Evidence generated

- Screenshots at key checkpoints are saved under:
  - `target/saleads-evidence/run-<timestamp>/`
- Final URLs for legal links are printed in test output.
- PASS/FAIL report for all requested sections is printed at the end of the test.
