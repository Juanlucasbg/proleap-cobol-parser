# SaleADS Mi Negocio Full Workflow Test

Playwright E2E test that validates the full **Mi Negocio** flow:

1. Login with Google.
2. Open sidebar menu `Negocio > Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate:
   - `Información General`
   - `Detalles de la Cuenta`
   - `Tus Negocios`
6. Validate legal links:
   - `Términos y Condiciones`
   - `Política de Privacidad`
7. Generate final PASS/FAIL report.

The test is environment-agnostic and does not hardcode domains.

## Requirements

- Node.js 18+
- Playwright browsers installed:

```bash
npx playwright install
```

## Usage

Set the login URL for the target environment (dev/staging/prod), then run:

```bash
SALEADS_START_URL="https://<your-saleads-environment>/login" npm test
```

If your execution environment already starts on the login page, you can omit `SALEADS_START_URL`.

## Evidence Generated

Screenshots and JSON evidence are generated under Playwright `test-results`:

- `01-dashboard-loaded.png`
- `02-mi-negocio-menu-expanded.png`
- `03-agregar-negocio-modal.png`
- `04-administrar-negocios-full-page.png`
- `05-terminos-y-condiciones.png`
- `06-politica-de-privacidad.png`
- `final-report.json`

## Final Report Fields

`final-report.json` includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
