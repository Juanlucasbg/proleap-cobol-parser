# SaleADS Mi Negocio full workflow test

This repository now includes an optional Playwright-based JUnit test that validates the complete "Mi Negocio" workflow requested for SaleADS.ai.

## Test class

- `io.proleap.cobol.ui.saleads.SaleadsMiNegocioFullWorkflowTest`

## What it validates

- Login with Google (and account selection for `juanlucasbarbiergarzon@gmail.com` by default)
- Left sidebar visibility after login
- `Negocio` -> `Mi Negocio` menu expansion
- `Agregar Negocio` modal content
- `Administrar Negocios` view sections
- `Información General`, `Detalles de la Cuenta`, `Tus Negocios`
- `Términos y Condiciones` and `Política de Privacidad` links, including popup/new-tab handling
- Screenshot evidence at key checkpoints
- Final PASS/FAIL report per requested field

## Configuration

The test is intentionally disabled by default so existing parser CI remains unaffected.

### Required

- `saleads.ui.enabled=true`
- `saleads.login.url=<current environment login URL>`

### Optional

- `saleads.google.account` (default: `juanlucasbarbiergarzon@gmail.com`)
- `saleads.headless` (default: `true`)
- `saleads.evidence.dir` (default: `target/saleads-evidence`)

Environment variable equivalents are also supported:

- `SALEADS_UI_ENABLED`
- `SALEADS_LOGIN_URL`
- `SALEADS_GOOGLE_ACCOUNT`
- `SALEADS_HEADLESS`
- `SALEADS_EVIDENCE_DIR`

## Example run

```bash
mvn -Dtest=SaleadsMiNegocioFullWorkflowTest \
    -Dsaleads.ui.enabled=true \
    -Dsaleads.login.url="https://<your-saleads-environment>/login" \
    test
```

The test prints a final report and stores screenshots in:

- `target/saleads-evidence/saleads-mi-negocio-full-test-<timestamp>/`
