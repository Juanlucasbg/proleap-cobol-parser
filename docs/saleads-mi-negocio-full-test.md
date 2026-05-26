# SaleADS Mi Negocio Full Workflow Test

This test automates the full `saleads_mi_negocio_full_test` flow, including:

- Login with Google
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Información General, Detalles de la Cuenta, Tus Negocios
- Términos y Condiciones + Política de Privacidad (new tab or same tab handling)
- Evidence capture via screenshots and legal URLs
- Final PASS/FAIL JSON report per required fields

## Location

- Test class: `src/test/java/io/proleap/cobol/e2e/saleads/SaleadsMiNegocioFullWorkflowTest.java`

## Environment-agnostic execution

Set the environment URL dynamically (no domain hardcoding):

```bash
export SALEADS_URL="https://<current-saleads-environment>/login"
mvn -Dtest=io.proleap.cobol.e2e.saleads.SaleadsMiNegocioFullWorkflowTest test
```

Optional flags:

```bash
export SALEADS_HEADLESS="true"   # default true
# or JVM properties:
mvn -Dsaleads.url="https://<env>/login" -Dsaleads.headless=false -Dtest=io.proleap.cobol.e2e.saleads.SaleadsMiNegocioFullWorkflowTest test
```

## Outputs

Artifacts are saved under:

- `target/saleads-mi-negocio/<timestamp>/screenshots/*.png`
- `target/saleads-mi-negocio/<timestamp>/saleads-mi-negocio-report.json`

The JSON report includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
