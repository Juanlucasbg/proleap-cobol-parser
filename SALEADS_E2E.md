## SaleADS Mi Negocio Full Workflow E2E

This repository now includes a Selenium-based JUnit test for the full SaleADS Mi Negocio workflow:

- Test class: `src/test/java/io/proleap/e2e/saleads/SaleadsMiNegocioFullTest.java`
- Scope:
  - Login with Google
  - Navigate `Negocio` -> `Mi Negocio`
  - Validate `Agregar Negocio` modal
  - Open and validate `Administrar Negocios` sections
  - Validate legal links (`Términos y Condiciones`, `Política de Privacidad`)
  - Handle same-tab and new-tab legal navigation
  - Capture screenshots at important checkpoints
  - Generate final PASS/FAIL report for each required validation field

### Safety gate

The E2E test is **disabled by default** and only runs when:

```bash
SALEADS_E2E_ENABLED=true
```

### Environment-agnostic configuration

Do not hardcode environment URLs in test code. Provide runtime values with environment variables:

- `SALEADS_LOGIN_URL` (recommended): login page URL for the current SaleADS environment
- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_BROWSER` (`chrome` default, or `firefox`)
- `SALEADS_HEADLESS` (`true` default)
- `SALEADS_WEBDRIVER_URL` (optional remote Selenium hub URL)
- `SALEADS_TIMEOUT_SECONDS` (default `30`)
- `SALEADS_PAGELOAD_TIMEOUT_SECONDS` (default `60`)
- `SALEADS_UI_SETTLE_MS` (default `700`)
- `SALEADS_EVIDENCE_DIR` (default `target/saleads-e2e-evidence`)

### Run command

Run only this test class:

```bash
SALEADS_E2E_ENABLED=true \
SALEADS_LOGIN_URL="https://<current-env-login-url>" \
mvn -Dtest=io.proleap.e2e.saleads.SaleadsMiNegocioFullTest test
```

### Evidence outputs

For each run, evidence is generated under:

```text
target/saleads-e2e-evidence/<timestamp>/
```

Including:

- Checkpoint screenshots (`*.png`)
- `final-report.txt` with PASS/FAIL status for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
