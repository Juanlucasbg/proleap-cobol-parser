# SaleADS E2E - Mi Negocio workflow

This folder contains an environment-agnostic Playwright test for the full **Mi Negocio** workflow:

- Login with Google
- Validate sidebar and Mi Negocio expansion
- Validate **Agregar Negocio** modal
- Validate **Administrar Negocios** sections
- Validate legal links (**Términos y Condiciones**, **Política de Privacidad**) including same-tab/new-tab behavior
- Capture checkpoint screenshots and a final PASS/FAIL report

## Run

```bash
cd e2e
npm install
npx playwright install --with-deps
SALEADS_LOGIN_URL="https://<current-env-domain>/login" npm run test:saleads-mi-negocio
```

## Environment notes

- The test is domain-agnostic and does not hardcode a specific environment URL.
- Set `SALEADS_LOGIN_URL` to the login route of the current environment.
- If your harness already opens the login page, you can leave `SALEADS_LOGIN_URL` unset, but the current page must not be `about:blank`.

## Expected account selector email

When Google account selection appears, the test attempts to click:

- `juanlucasbarbiergarzon@gmail.com`

If the selector is not shown (already authenticated session), the test continues.
