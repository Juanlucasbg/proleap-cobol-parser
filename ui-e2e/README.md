# SaleADS UI E2E

This folder contains a Playwright test that validates the complete **Mi Negocio** workflow:

- Google login
- Sidebar and Mi Negocio menu expansion
- Agregar Negocio modal validations
- Administrar Negocios validations
- Información General / Detalles de la Cuenta / Tus Negocios checks
- Términos y Condiciones + Política de Privacidad link validations (including new-tab handling)
- Final PASS/FAIL report generation

## Run

Install dependencies (already tracked in `package-lock.json`):

```bash
npm --prefix ui-e2e install
```

Run only this test:

```bash
npm --prefix ui-e2e run test:saleads-mi-negocio
```

Optional environment variable:

- `SALEADS_LOGIN_URL`: login page URL if the browser is not already on the login screen.

The test does **not** hardcode any SaleADS domain and uses visible-text selectors whenever possible.

## Artifacts

After execution, the suite writes:

- `ui-e2e/artifacts/screenshots/*.png`
- `ui-e2e/artifacts/saleads-mi-negocio-final-report.json`
- `ui-e2e/artifacts/saleads-mi-negocio-final-report.md`
