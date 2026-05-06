## SaleADS Mi Negocio full workflow E2E

This repository now includes a Selenium/JUnit end-to-end test that validates the full "Mi Negocio" workflow described in the automation request.

### Test class

- `src/test/java/io/proleap/saleads/e2e/SaleadsMiNegocioFullTest.java`

### What it validates

The test produces PASS/FAIL for:

1. Login
2. Mi Negocio menu
3. Agregar Negocio modal
4. Administrar Negocios view
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones
9. Politica de Privacidad

It also:

- waits for UI load after each click action;
- captures screenshots at key checkpoints and failure points;
- handles legal links that open either in the same tab or a new tab;
- captures and prints final URLs for legal pages;
- returns to the application tab after legal-page validation.

### Environment variables

Set these before running:

- `SALEADS_URL` (required): login URL for the current SaleADS environment.
  - No domain is hardcoded in the test.
- `SALEADS_GOOGLE_ACCOUNT_EMAIL` (optional):
  - default: `juanlucasbarbiergarzon@gmail.com`
- `SALEADS_HEADLESS` (optional):
  - default: `true`
  - set to `false` to watch browser interactions.

### Run

```bash
SALEADS_URL="https://<current-env-login-url>" \
SALEADS_GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" \
mvn -Dtest=io.proleap.saleads.e2e.SaleadsMiNegocioFullTest test
```

### Evidence artifacts

- Screenshots are saved under:
  - `target/saleads-evidence/<timestamp>/`
- Final report is printed in test output as:
  - `===== SaleADS Mi Negocio Final Report =====`
