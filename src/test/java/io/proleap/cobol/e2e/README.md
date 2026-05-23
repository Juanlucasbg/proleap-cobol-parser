# SaleADS Mi Negocio E2E test

This folder contains a Playwright-based UI test for validating the full
SaleADS "Mi Negocio" workflow end to end:

- Login with Google
- Open and validate the "Mi Negocio" menu
- Validate "Agregar Negocio" modal
- Open "Administrar Negocios"
- Validate account sections and legal links
- Emit a final PASS/FAIL report by validation area
- Save screenshots for key checkpoints

## Test class

- `io.proleap.cobol.e2e.SaleadsMiNegocioWorkflowTest`

## Configuration

The test intentionally does not hardcode any SaleADS URL. It reads the first
non-empty variable from:

1. `SALEADS_LOGIN_URL`
2. `SALEADS_URL`
3. `BASE_URL`

Optional environment variables:

- `SALEADS_HEADLESS` (default `true`)
- `SALEADS_BROWSER` (`chromium`, `firefox`, `webkit`; default `chromium`)
- `SALEADS_EXPECTED_USER_NAME` (if set, used for strict name validation)

## Evidence output

Screenshots are saved under:

- `target/saleads-evidence/<timestamp>/`

The test prints final legal URLs and PASS/FAIL results to stdout.
