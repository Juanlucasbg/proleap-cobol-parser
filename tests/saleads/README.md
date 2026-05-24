# SaleADS Mi Negocio workflow test

This folder contains an end-to-end Playwright test for the full **Mi Negocio** workflow:

- Google login
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios validations
- Informacion General / Detalles de la Cuenta / Tus Negocios checks
- Terminos y Condiciones and Politica de Privacidad validations
- Final PASS/FAIL JSON report attachment

## Environment-agnostic behavior

The test does not hardcode a SaleADS domain.

- If the browser session is already at the login page, it continues from the current URL.
- If Playwright starts at `about:blank`, set `SALEADS_LOGIN_URL` to the login URL for the current environment.

## Run

```bash
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm run test:e2e
```

Generated artifacts:

- `test-results/` (screenshots + attachments)
- `playwright-report/` (HTML report)
