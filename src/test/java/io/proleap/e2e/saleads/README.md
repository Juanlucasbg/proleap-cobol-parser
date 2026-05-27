# SaleADS Mi Negocio E2E Workflow

This folder contains an opt-in Playwright E2E test that validates the full
"Mi Negocio" workflow requested in the automation prompt:

- Google login flow
- Mi Negocio menu expansion
- Agregar Negocio modal validations
- Administrar Negocios page validations
- Informacion General / Detalles de la Cuenta / Tus Negocios checks
- Legal links (Terminos y Condiciones / Politica de Privacidad), including
  handling new-tab navigation
- Evidence capture (screenshots + final JSON report with PASS/FAIL fields)

## Run

Set the environment variables (or equivalent `-D` JVM properties):

- `SALEADS_E2E_ENABLED=true`
- `SALEADS_LOGIN_URL=<current environment login URL>`
- optional: `SALEADS_HEADLESS=false` to watch the browser interactively

Then run the test class:

```bash
mvn -Dtest=io.proleap.e2e.saleads.SaleadsMiNegocioWorkflowTest test
```

## Evidence output

The test writes artifacts to:

`target/saleads-e2e/<timestamp>/`

including:

- `screenshots/*.png`
- `final-report.json`
