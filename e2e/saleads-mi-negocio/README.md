# SaleADS Mi Negocio full workflow test

Playwright E2E test for the full **Mi Negocio** workflow:

1. Google login
2. Mi Negocio sidebar expansion
3. Agregar Negocio modal validation
4. Administrar Negocios page validation
5. Información General validation
6. Detalles de la Cuenta validation
7. Tus Negocios validation
8. Términos y Condiciones validation
9. Política de Privacidad validation
10. Final PASS/FAIL report

## Environment-agnostic behavior

- No hardcoded domain is used.
- Set `SALEADS_LOGIN_URL` to any environment (dev, staging, production).
- If your harness already opens the login page, `SALEADS_LOGIN_URL` is optional.

## Run

```bash
cd e2e/saleads-mi-negocio
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<your-saleads-environment>/login" npm run test:mi-negocio
```

## Evidence generated

The test captures screenshots at key checkpoints in Playwright's output folder (`test-results`), including:

- Dashboard loaded
- Mi Negocio expanded menu
- Agregar Negocio modal
- Administrar Negocios page
- Términos y Condiciones page
- Política de Privacidad page

The run also prints:

- PASS/FAIL report per requested section
- Final URLs for legal pages
