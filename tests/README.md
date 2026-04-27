# SaleADS Mi Negocio E2E Automation

This folder contains the `saleads_mi_negocio_full_test` Playwright test, designed to validate
the full "Mi Negocio" workflow after Google login.

## Environment-agnostic execution

The test intentionally does not hardcode a SaleADS domain.

- If `SALEADS_LOGIN_URL` is provided, the test navigates to that URL.
- If it is not provided, the test assumes the browser is already on the SaleADS login page.

## Run commands

1. Install dependencies:

   ```bash
   npm install
   ```

2. Install Chromium runtime for Playwright:

   ```bash
   npm run playwright:install
   ```

3. Execute the test:

   ```bash
   SALEADS_LOGIN_URL="https://<current-env-login-url>" npm run test:saleads:mi-negocio
   ```

## Evidence outputs

The test stores screenshot and JSON evidence in:

`saleads-evidence/saleads_mi_negocio_full_test/`

Including:

- Step screenshots (dashboard, menu, modal, account page, legal pages)
- `final-report.json` with PASS/FAIL/SKIPPED status for each requested validation group
