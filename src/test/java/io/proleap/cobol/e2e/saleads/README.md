# SaleADS Mi Negocio E2E workflow test

This folder contains an environment-agnostic Playwright test that validates
the full Mi Negocio workflow described in the automation request.

## Test class

- `SaleadsMiNegocioWorkflowTest`

## What it validates

The test executes and reports PASS/FAIL for:

1. Login
2. Mi Negocio menu
3. Agregar Negocio modal
4. Administrar Negocios view
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones
9. Politica de Privacidad

It also captures screenshots at key checkpoints under:

- `target/saleads-evidence/<timestamp>/`

And prints final legal document URLs in the test output.

## Runtime settings

Configure via environment variables (or Java `-D` properties):

- `SALEADS_LOGIN_URL` (required) or `-Dsaleads.login.url=...`
  - Login URL for the target SaleADS environment.
  - The test does not hardcode any specific domain.
- `SALEADS_HEADLESS` (optional, default `true`) or `-Dsaleads.headless=...`
- `SALEADS_EXPECTED_USER_EMAIL` (optional, default
  `juanlucasbarbiergarzon@gmail.com`) or `-Dsaleads.expected.user.email=...`
- `SALEADS_EXPECTED_USER_NAME` (optional) or `-Dsaleads.expected.user.name=...`

## Run only this test

```bash
SALEADS_LOGIN_URL="https://<your-env-login>" \
mvn -Pskip.nist.tests -Dtest=io.proleap.cobol.e2e.saleads.SaleadsMiNegocioWorkflowTest test
```

## Notes

- Selectors prioritize visible text and role-based matching.
- After each click, the test waits for UI load/settle.
- If legal links open in a new tab, that tab is validated and closed, then the
  test returns to the app tab.
