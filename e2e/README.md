# SaleADS Mi Negocio E2E

This folder contains a Playwright end-to-end automation for the workflow:

1. Login with Google
2. Navigate to `Negocio > Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open and validate `Administrar Negocios`
5. Validate legal links (`Terminos y Condiciones`, `Politica de Privacidad`)
6. Produce a final PASS/FAIL report per requested field

## Why this works across environments

- The test does not hardcode any SaleADS domain.
- Use `SALEADS_LOGIN_URL` (or `BASE_URL`) for the current environment.
- Element targeting is based on visible text and semantic roles.

## Run

```bash
cd e2e
npm install
npm run install:browsers
SALEADS_LOGIN_URL="https://<your-environment-login-page>" npm test
```

If your runner already opens the login page before test start, you can omit `SALEADS_LOGIN_URL`.

## Output evidence

- Checkpoint screenshots are saved in Playwright test output artifacts.
- A `final-report.json` attachment is generated with:
  - PASS/FAIL per report field
  - Legal URLs captured
  - Any validation errors
