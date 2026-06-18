# SaleADS Mi Negocio full workflow test

This repository now includes a Playwright-based JUnit test:

- `src/test/java/io/proleap/cobol/e2e/SaleadsMiNegocioFullWorkflowTest.java`

## What it validates

The test executes the requested SaleADS "Mi Negocio" flow end-to-end:

1. Login with Google (including account selection for `juanlucasbarbiergarzon@gmail.com` when shown)
2. Open and validate the `Mi Negocio` submenu
3. Open and validate the `Crear Nuevo Negocio` modal
4. Open `Administrar Negocios` and validate account sections
5. Validate `Informacion General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Terminos y Condiciones` (popup/new tab or same-tab navigation)
9. Validate `Politica de Privacidad` (popup/new tab or same-tab navigation)
10. Emit a final PASS/FAIL report for each required field

The test prefers visible text selectors and waits for UI load after each click.

## Evidence output

Checkpoint screenshots are stored in:

- `target/saleads-evidence/<timestamp>/`

The report is printed to test output with PASS/FAIL status and legal page URLs.

## Run instructions

The E2E test is intentionally gated so it only runs when explicitly enabled.

Required env var:

- `SALEADS_RUN_E2E=true`

Optional env vars:

- `SALEADS_BASE_URL=https://<current-env-saleads-login-page>`
- `SALEADS_HEADLESS=true|false` (default: `true`)

Example:

```bash
SALEADS_RUN_E2E=true \
SALEADS_BASE_URL="https://example.saleads.ai/login" \
mvn -Dtest=SaleadsMiNegocioFullWorkflowTest test
```

If `SALEADS_BASE_URL` is not provided, the test expects a preloaded login page context; otherwise it fails fast with a descriptive message.
