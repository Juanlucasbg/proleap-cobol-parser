# SaleADS Mi Negocio E2E

This Playwright spec validates the full **Mi Negocio** workflow described in
`saleads_mi_negocio_full_test`, including:

- Login with Google
- Mi Negocio menu expansion
- Agregar Negocio modal checks
- Administrar Negocios view checks
- Información General, Detalles de la Cuenta, Tus Negocios validations
- Términos y Condiciones and Política de Privacidad validation (including new-tab handling)
- Final PASS/FAIL report attachment

## Run

Install browsers (first run only):

```bash
npx playwright install
```

Run headless:

```bash
SALEADS_LOGIN_URL="https://<your-env>/login" npm run test:e2e -- e2e/saleads-mi-negocio-full.spec.js
```

Run headed:

```bash
SALEADS_LOGIN_URL="https://<your-env>/login" npm run test:e2e:headed -- e2e/saleads-mi-negocio-full.spec.js
```

## Notes

- No domain is hardcoded. The test reads `SALEADS_LOGIN_URL` (or `SALEADS_URL`, `BASE_URL`, `PLAYWRIGHT_BASE_URL`).
- If no URL is provided, it expects the browser to already be on the login page.
- Screenshots are attached at key checkpoints and stored in Playwright output artifacts.
