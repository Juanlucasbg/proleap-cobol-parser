# SaleADS Mi Negocio full workflow test

This folder contains the Playwright automation for:

- `saleads_mi_negocio_full_test`

The scenario validates end-to-end behavior for:

1. Login with Google.
2. Mi Negocio menu expansion.
3. Agregar Negocio modal fields and actions.
4. Administrar Negocios account sections.
5. Información General.
6. Detalles de la Cuenta.
7. Tus Negocios.
8. Términos y Condiciones (including final URL capture).
9. Política de Privacidad (including final URL capture).

## Run

```bash
cd automation/saleads-mi-negocio
npm install
SALEADS_LOGIN_URL="https://<current-env-login-page>" npm test
```

## Notes

- No environment-specific domain is hardcoded.
- Selectors prefer visible text in Spanish where available.
- The test waits for UI load after each click.
- If legal pages open in a new tab, the test validates content and returns to the app.

## Evidence output

- Checkpoint screenshots and the final JSON report are written to Playwright test output:
  - `test-results/.../*.png`
  - `test-results/.../saleads-mi-negocio-report.json`
