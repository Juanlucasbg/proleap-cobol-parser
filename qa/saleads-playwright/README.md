# SaleADS Mi Negocio Workflow Test

This folder contains the Playwright scenario `saleads_mi_negocio_full_test` that validates:

- Login with Google (and optional account selection)
- Mi Negocio menu expansion
- Agregar Negocio modal content
- Administrar Negocios account view sections
- Informacion General / Detalles de la Cuenta / Tus Negocios
- Terminos y Condiciones and Politica de Privacidad links (new-tab or same-tab)
- Evidence screenshots and final PASS/FAIL report attachment

## Run

```bash
cd qa/saleads-playwright
npm install
npx playwright install --with-deps
npx playwright test tests/saleads-mi-negocio-full-test.spec.js
```

## Environment-agnostic navigation

The test does not hardcode a SaleADS domain. If the browser starts on `about:blank`, provide one of:

- `SALEADS_LOGIN_URL`
- `SALEADS_BASE_URL`

Example:

```bash
SALEADS_LOGIN_URL="https://<current-env-host>/login" npx playwright test tests/saleads-mi-negocio-full-test.spec.js
```

## Output

- Checkpoint screenshots are attached in Playwright output.
- `final-report` JSON attachment includes PASS/FAIL per required field and legal page URLs.
