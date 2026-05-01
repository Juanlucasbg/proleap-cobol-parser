# SaleADS E2E: Mi Negocio full workflow

This repository is primarily a COBOL parser project.  
To keep normal parser test runs stable, the SaleADS browser test is opt-in and only runs when explicitly enabled.

## Test class

- `io.proleap.cobol.e2e.SaleadsMiNegocioWorkflowTest`

## What it validates

The test automates the requested flow end-to-end:

1. Login with Google (including account picker handling for `juanlucasbarbiergarzon@gmail.com`)
2. Open **Mi Negocio** menu and validate submenu
3. Open and validate **Agregar Negocio** modal
4. Open **Administrar Negocios** and validate sections
5. Validate **Información General**
6. Validate **Detalles de la Cuenta**
7. Validate **Tus Negocios**
8. Validate **Términos y Condiciones** (supports same-tab or new-tab)
9. Validate **Política de Privacidad** (supports same-tab or new-tab)
10. Print final PASS/FAIL report for all required fields

The test captures screenshots at key checkpoints and stores them under:

- `target/saleads-evidence/<timestamp>/`

## Run prerequisites

1. Chrome browser installed in the execution environment.
2. A matching ChromeDriver available on `PATH` (or configured via Selenium system properties).
3. Access to a SaleADS login page for the current environment.

## How to run

Pass the login page URL at runtime (no hardcoded domain):

```bash
mvn -Dtest=SaleadsMiNegocioWorkflowTest \
    -Dsaleads.e2e.enabled=true \
    -Dsaleads.login.url="https://<current-saleads-login-url>" \
    test
```

Optional:

- `-Dsaleads.headless=false` to watch execution in a visible browser window.
- You may also provide URL via env var: `SALEADS_LOGIN_URL`.

## Notes about environment agnosticism

- The test does not hardcode a specific SaleADS domain.
- It relies primarily on visible text selectors and basic semantic containers.
- It explicitly waits for UI readiness after each click step.
