## SaleADS E2E tests

This folder contains Playwright end-to-end tests for SaleADS workflows.

### Implemented test

- `tests/saleads-mi-negocio.spec.js`: full Mi Negocio module workflow, including:
  - Login with Google from the current environment login page.
  - Sidebar validation and Mi Negocio menu expansion.
  - Agregar Negocio modal validation.
  - Administrar Negocios account view validations.
  - Informacion General, Detalles de la Cuenta, and Tus Negocios validations.
  - Legal links (Terminos y Condiciones / Politica de Privacidad) with new-tab handling.
  - Step-level screenshot evidence and final PASS/FAIL report JSON.

### Important execution note

The test intentionally does not hardcode a base URL. It assumes the browser already starts on the SaleADS login page for whichever environment is under test.

### Run

From the repo root:

```bash
cd e2e
npm install
npx playwright install chromium
npm run test:e2e:mi-negocio
```

Optional environment variables:

- `SALEADS_GOOGLE_ACCOUNT`: preferred account email for the Google account picker.
  - Default: `juanlucasbarbiergarzon@gmail.com`

### Artifacts

- Screenshots: `e2e/test-results/screenshots/`
- Final report JSON: `e2e/test-results/saleads-mi-negocio-report.json`
