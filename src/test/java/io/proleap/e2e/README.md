# SaleADS Mi Negocio full workflow E2E

This folder contains an end-to-end UI test:

- `SaleadsMiNegocioFullTest`

The test executes the complete workflow requested for SaleADS:

1. Login with Google.
2. Open `Negocio` -> `Mi Negocio`.
3. Validate `Agregar Negocio` modal fields and actions.
4. Open `Administrar Negocios`.
5. Validate:
   - `Información General`
   - `Detalles de la Cuenta`
   - `Tus Negocios`
6. Validate legal links:
   - `Términos y Condiciones`
   - `Política de Privacidad`
7. Capture screenshots and produce a final PASS/FAIL report.

## Configuration

The test is intentionally environment-agnostic:

- No domain is hardcoded.
- The login URL must be provided at runtime.

Runtime settings can be passed as JVM properties or environment variables:

- `saleads.e2e.enabled` / `SALEADS_E2E_ENABLED` (required to run, default `false`)
- `saleads.loginUrl` / `SALEADS_LOGIN_URL` (required)
- `saleads.googleEmail` / `SALEADS_GOOGLE_EMAIL` (default `juanlucasbarbiergarzon@gmail.com`)
- `saleads.userName` / `SALEADS_USER_NAME` (default: local part of email)
- `saleads.headless` / `SALEADS_HEADLESS` (default `true`)
- `saleads.uiWaitMs` / `SALEADS_UI_WAIT_MS` (default `1200`)

## Run

Install browser binaries once:

```bash
mvn -DskipTests test-compile exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

Run only this workflow test:

```bash
mvn -Dtest=io.proleap.e2e.SaleadsMiNegocioFullTest \
    -Dsaleads.e2e.enabled=true \
    -Dsaleads.loginUrl="https://<your-env-login-page>" \
    test
```

## Evidence output

Artifacts are written under:

- `target/saleads-evidence/<timestamp>/`

Including:

- checkpoint screenshots
- `final-report.txt`
- `final-report.json`
