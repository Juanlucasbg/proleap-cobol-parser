# SaleADS Mi Negocio workflow E2E

This repository now includes a Playwright end-to-end test:

- `tests/saleads_mi_negocio_full_test.spec.js`

## What it validates

The test automates the complete workflow requested for **Mi Negocio**:

1. Google login flow (including optional account chooser selection).
2. Sidebar navigation to **Negocio > Mi Negocio**.
3. **Agregar Negocio** modal validation.
4. **Administrar Negocios** page validation.
5. Section validations:
   - Información General
   - Detalles de la Cuenta
   - Tus Negocios
6. Legal links validation:
   - Términos y Condiciones
   - Política de Privacidad
   including popup/new-tab handling and return to the app tab.

Screenshots are captured at each important checkpoint and on failures.

## Environment-agnostic execution

The test does **not** hardcode any SaleADS domain.
Provide one of these variables at runtime:

- `SALEADS_START_URL`
- `SALEADS_LOGIN_URL`
- `BASE_URL`

Example:

```bash
SALEADS_START_URL="https://<current-environment-login-url>" npm run test:e2e -- --grep saleads_mi_negocio_full_test
```

## Output artifacts

Playwright stores artifacts under `test-results/` and HTML report under `playwright-report/`.
The test also attaches and prints a final PASS/FAIL JSON report with the requested fields.
