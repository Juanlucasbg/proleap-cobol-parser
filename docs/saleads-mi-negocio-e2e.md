# SaleADS Mi Negocio Full Workflow E2E

This repository now includes a Playwright + JUnit test that automates:

- Google login (first step only, then continues)
- `Negocio` -> `Mi Negocio` menu validation
- `Agregar Negocio` modal validation
- `Administrar Negocios` page validation
- `Informacion General`, `Detalles de la Cuenta`, `Tus Negocios` checks
- `Terminos y Condiciones` and `Politica de Privacidad` validation
- screenshot capture at key checkpoints
- final PASS/FAIL report per requested field

## Test class

`src/test/java/io/proleap/saleads/e2e/SaleadsMiNegocioFullTest.java`

## Important behavior

- No domain is hardcoded.
- The test can run against any SaleADS environment via runtime config.
- Element selection prioritizes visible text.
- After each click, the test waits for UI readiness.
- If legal links open a new tab, that tab is validated and then closed; focus returns to the app tab.
- Evidence screenshots are stored under:
  - `target/saleads-evidence/<timestamped-folder>/`

## Required runtime configuration

Enable test execution:

- JVM property: `-Dsaleads.e2e.enabled=true`
  or
- env var: `SALEADS_E2E_ENABLED=true`

Provide one of:

1) Login URL (test opens browser and navigates)
- JVM property: `-Dsaleads.loginUrl=<saleads-login-url>`
  or
- env var: `SALEADS_LOGIN_URL=<saleads-login-url>`

2) CDP URL (attach to an already-open browser session)
- JVM property: `-Dsaleads.cdpUrl=<ws-endpoint>`
  or
- env var: `SALEADS_CDP_URL=<ws-endpoint>`

Optional:

- `-Dsaleads.headless=false` or `SALEADS_HEADLESS=false` (default is `true`)

## Example command

```bash
mvn -Dtest=SaleadsMiNegocioFullTest \
    -Dsaleads.e2e.enabled=true \
    -Dsaleads.loginUrl="https://<current-saleads-env>/login" \
    -Dsaleads.headless=false \
    test
```

## Final report fields

The test emits PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Informacion General
- Detalles de la Cuenta
- Tus Negocios
- Terminos y Condiciones
- Politica de Privacidad
