# SaleADS Mi Negocio E2E

This Playwright test validates the full **Mi Negocio** workflow requested in the automation prompt:

1. Google login entry point
2. Mi Negocio menu expansion
3. Agregar Negocio modal checks
4. Administrar Negocios page checks
5. Información General checks
6. Detalles de la Cuenta checks
7. Tus Negocios checks
8. Términos y Condiciones link validation (same tab or new tab)
9. Política de Privacidad link validation (same tab or new tab)
10. Final PASS/FAIL report per step

## Environment-agnostic execution

The test intentionally does **not** hardcode a domain.

Set one of these environment variables to the current environment login page:

- `SALEADS_LOGIN_URL`
- `SALEADS_URL`

Example:

```bash
SALEADS_LOGIN_URL="https://<current-env>/login" npm run test:e2e -- --grep "SaleADS Mi Negocio full workflow validation"
```

## Browser/account assumptions

- If Google account selection appears, the test attempts to select:
  - `juanlucasbarbiergarzon@gmail.com`
- The flow continues after login and does not stop at dashboard validation.

## Evidence output

Playwright artifacts are generated in `test-results/`, including:

- Step screenshots at key checkpoints
- `final-report.json` attachment with:
  - PASS/FAIL by requested report field
  - Captured final URLs for legal links
  - Any validation failures
