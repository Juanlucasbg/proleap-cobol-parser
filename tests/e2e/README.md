# SaleADS E2E - Mi Negocio Full Workflow

This folder contains the Playwright test `saleads_mi_negocio_full_test`, which validates the full "Mi Negocio" workflow after Google login.

## Environment-agnostic execution

The test is designed to work across SaleADS environments without hardcoding a domain.

- If the browser/session already starts on the SaleADS login page, run directly.
- If you need navigation from scratch, provide:

```bash
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm run test:saleads:mi-negocio
```

## What the test validates

1. Login with Google and main app/sidebar visibility.
2. Expand "Mi Negocio" menu and validate submenu options.
3. Open and validate "Crear Nuevo Negocio" modal.
4. Open "Administrar Negocios" view and validate key sections.
5. Validate "Información General".
6. Validate "Detalles de la Cuenta".
7. Validate "Tus Negocios".
8. Validate "Términos y Condiciones" including URL capture.
9. Validate "Política de Privacidad" including URL capture.
10. Produce PASS/FAIL final report per requested fields.

## Evidence

The test captures screenshots at key checkpoints and stores artifacts under Playwright outputs:

- `test-results/**/screenshots/*.png`
- `test-results/**/saleads-mi-negocio-report.json`
- `test-results/**/saleads-mi-negocio-report.txt`
- `playwright-report/` (HTML report)
