# SaleADS E2E - Mi Negocio Full Workflow

This folder contains a Playwright test that automates the full workflow requested in `saleads_mi_negocio_full_test`:

- Google login flow
- Sidebar `Mi Negocio` menu expansion
- `Agregar Negocio` modal validation
- `Administrar Negocios` page validation
- `Información General`, `Detalles de la Cuenta`, `Tus Negocios` validations
- Legal link validations for:
  - `Términos y Condiciones`
  - `Política de Privacidad`
- Checkpoint screenshots and final PASS/FAIL report

## Test file

- `tests/saleads-mi-negocio-full.spec.ts`

## Environment support

The test is environment-agnostic and does not hardcode a specific SaleADS domain.

Use one of these modes:

1. **Provide URL dynamically** (recommended for CI):
   - Set `SALEADS_LOGIN_URL` to the login page URL of the target environment.
2. **Start from an already-open login page**:
   - Launch Playwright with a preloaded login page in your local debug flow.

## Run

```bash
cd e2e
npm install
npx playwright install --with-deps
SALEADS_LOGIN_URL="https://<your-environment>/login" npm run test:saleads-mi-negocio
```

## Output evidence

The test captures screenshots at key checkpoints:

- Dashboard loaded
- Expanded `Mi Negocio` menu
- `Crear Nuevo Negocio` modal
- Full `Administrar Negocios` page
- `Términos y Condiciones` page
- `Política de Privacidad` page

A final JSON report is attached in Playwright artifacts and printed to stdout with:

- PASS/FAIL per required section
- Final URLs captured for legal pages
