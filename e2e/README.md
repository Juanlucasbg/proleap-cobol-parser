# SaleADS Mi Negocio E2E

Automated Playwright test for:

- Google login
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios sections validation
- Legal links validation (including new-tab handling)
- Screenshot evidence and PASS/FAIL report generation

## Environment-agnostic execution

This suite does not hardcode a domain. Provide the login URL for the target SaleADS environment at runtime.

```bash
cd /workspace/e2e
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<current-environment>/login" npm run test:mi-negocio
```

If your environment already injects `BASE_URL`, the test can use it instead of `SALEADS_LOGIN_URL`.

## Output artifacts

After execution, artifacts are stored in:

- `artifacts/saleads-mi-negocio/screenshots/`
- `artifacts/saleads-mi-negocio/final-report.json`
- `artifacts/saleads-mi-negocio/final-report.md`

The final report includes PASS/FAIL status for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
