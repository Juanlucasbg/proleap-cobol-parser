# SaleADS Mi Negocio E2E

This folder contains an environment-agnostic Playwright test for the full workflow:

- Google login
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Informacion General / Detalles de la Cuenta / Tus Negocios
- Terminos y Condiciones + Politica de Privacidad (including new-tab handling)
- Final PASS/FAIL report per requested validation block

## Run

```bash
cd /workspace/e2e
npm install
npx playwright install --with-deps
SALEADS_LOGIN_URL="https://<current-environment-login-url>" npm run test:saleads
```

### Notes

- No domain is hardcoded in the test.
- Use `SALEADS_LOGIN_URL` (or `SALEADS_BASE_URL`) for whichever environment is under test.
- The test captures screenshots at key checkpoints and records legal final URLs in test output.
