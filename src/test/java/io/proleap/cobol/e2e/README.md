# SaleADS Mi Negocio E2E workflow test

This folder includes `SaleadsMiNegocioWorkflowTest`, a Selenium + JUnit test that validates the complete **Mi Negocio** workflow requested by automation:

- Login with Google
- Expand **Mi Negocio** menu
- Validate **Agregar Negocio** modal
- Open and validate **Administrar Negocios**
- Validate **Información General**, **Detalles de la Cuenta**, **Tus Negocios**
- Validate **Términos y Condiciones** and **Política de Privacidad** (same tab or new tab)
- Save screenshots at key checkpoints and write a final PASS/FAIL report

## Configuration

The test is environment-agnostic and does not hardcode any domain. Use env vars or JVM properties:

- `SALEADS_LOGIN_URL` (`saleads.loginUrl`): login page URL for the current environment.
- `SALEADS_GOOGLE_ACCOUNT` (`saleads.googleAccount`): defaults to `juanlucasbarbiergarzon@gmail.com`.
- `SALEADS_TIMEOUT_SECONDS` (`saleads.timeoutSeconds`): explicit wait timeout (default `30`).
- `SALEADS_HEADLESS` (`saleads.headless`): `true`/`false` (default `false`).
- `SALEADS_CHROME_DEBUGGER` (`saleads.chromeDebugger`): optional debugger address to attach to an existing Chrome session.

## Running only this test

```bash
mvn -Dtest=io.proleap.cobol.e2e.SaleadsMiNegocioWorkflowTest test
```

## Evidence output

Each run stores outputs in:

`target/saleads-e2e/<timestamp>/`

- PNG screenshots for key checkpoints
- `final-report.md` with PASS/FAIL for all required report fields and captured legal URLs
