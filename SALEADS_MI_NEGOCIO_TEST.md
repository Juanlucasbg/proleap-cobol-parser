# SaleADS Mi Negocio Full Workflow Test

This repository now includes an opt-in Selenium test that validates the full **Mi Negocio** workflow after Google login:

- Login with Google
- Open and validate **Mi Negocio** menu
- Validate **Agregar Negocio** modal
- Open **Administrar Negocios**
- Validate:
  - **Información General**
  - **Detalles de la Cuenta**
  - **Tus Negocios**
  - **Términos y Condiciones**
  - **Política de Privacidad**
- Generate a final PASS/FAIL report per validation step

## Test class

`src/test/java/io/proleap/saleads/e2e/SaleadsMiNegocioWorkflowIT.java`

## Run

Use any SaleADS environment URL (dev/staging/prod) through a property or environment variable.

```bash
mvn -Dtest=io.proleap.saleads.e2e.SaleadsMiNegocioWorkflowIT -Dsaleads.loginUrl="<current_environment_login_url>" test
```

or:

```bash
SALEADS_LOGIN_URL="<current_environment_login_url>" mvn -Dtest=io.proleap.saleads.e2e.SaleadsMiNegocioWorkflowIT test
```

### Optional flags

- `-Dsaleads.headless=true` or `SALEADS_HEADLESS=true` to run headless.

## Evidence output

Artifacts are generated under:

`target/saleads-evidence/<timestamp>/`

including:

- Checkpoint screenshots
- `final-report.txt` with PASS/FAIL status for each required validation field
