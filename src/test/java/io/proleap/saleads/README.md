# SaleADS Mi Negocio Workflow E2E Test

This package contains an opt-in Playwright test that validates the full `Mi Negocio` workflow:

- Google login
- Mi Negocio menu expansion
- Agregar Negocio modal
- Administrar Negocios view
- Informacion General
- Detalles de la Cuenta
- Tus Negocios
- Terminos y Condiciones
- Politica de Privacidad

## Environment variables

- `SALEADS_E2E_ENABLED=true` (required, otherwise test is skipped)
- `SALEADS_URL=https://<current-environment-login-page>` (required, no hardcoded domain in code)
- `SALEADS_GOOGLE_ACCOUNT_EMAIL=juanlucasbarbiergarzon@gmail.com` (optional override)
- `SALEADS_EXPECTED_USER_NAME=<expected user display name>` (optional stricter check)
- `SALEADS_HEADLESS=true|false` (optional, default `true`)

## Run

```bash
mvn -Dtest=io.proleap.saleads.SaleadsMiNegocioWorkflowTest test
```

## Evidence and final report

- Screenshots are written to `target/saleads-evidence/<timestamp>/`.
- The test prints a final PASS/FAIL report for each requested validation field.
- Final URLs for legal pages are included in the printed report.
