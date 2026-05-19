# SaleADS Mi Negocio Full Workflow E2E

This repository now includes a Selenium workflow test that validates the full `Mi Negocio` path end-to-end:

- `src/test/java/io/proleap/e2e/SaleadsMiNegocioWorkflowIT.java`

## Why this test is environment-agnostic

- No URL/domain is hardcoded.
- The login page URL is provided at runtime via `SALEADS_LOGIN_URL`.
- The same script can be used in dev, staging, or production.

## Required environment variables

- `SALEADS_E2E_ENABLED=true`
- `SALEADS_LOGIN_URL=<current SaleADS login URL>`

Optional:

- `SALEADS_HEADLESS=true|false` (default: `true`)
- `SALEADS_TIMEOUT_SECONDS=<seconds>` (default: `30`)

## Run

```bash
mvn -Dtest=io.proleap.e2e.SaleadsMiNegocioWorkflowIT test
```

## Evidence and report output

The test writes screenshots and a final PASS/FAIL report here:

- `target/saleads-evidence/<timestamp>/`

Generated files include:

- Dashboard screenshot after login
- Expanded `Mi Negocio` menu screenshot
- `Agregar Negocio` modal screenshot
- `Administrar Negocios` page screenshot
- `Términos y Condiciones` screenshot
- `Política de Privacidad` screenshot
- `10-final-report.txt` with:
  - PASS/FAIL per required validation field
  - final URL for legal pages
