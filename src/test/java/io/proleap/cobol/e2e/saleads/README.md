# SaleADS - Mi Negocio full workflow test

This test automates the full "Mi Negocio" flow requested for SaleADS.ai:

- Login with Google (and account selection when shown).
- Open `Negocio` -> `Mi Negocio`.
- Validate `Agregar Negocio` modal.
- Open `Administrar Negocios`.
- Validate:
  - `Información General`
  - `Detalles de la Cuenta`
  - `Tus Negocios`
- Validate legal links:
  - `Términos y Condiciones`
  - `Política de Privacidad`
- Capture screenshots at key checkpoints and print a final PASS/FAIL report.

## Location

`src/test/java/io/proleap/cobol/e2e/saleads/SaleadsMiNegocioFullWorkflowIT.java`

## Environment-agnostic configuration

The test intentionally does not hardcode any SaleADS domain.

- `SALEADS_LOGIN_URL` (optional): login URL for the current environment.
- `SALEADS_APP_URL` (optional fallback): same as above.
- `SALEADS_HEADLESS` (optional, default `true`): set to `false` to run headed.

If no URL env var is provided, the test continues with whatever page is currently open in the Playwright context.

## Run

```bash
mvn -DskipTests test-compile
mvn -Dtest=io.proleap.cobol.e2e.saleads.SaleadsMiNegocioFullWorkflowIT test
```

Screenshots are saved under:

`target/saleads-mi-negocio-screenshots/<timestamp>/`
