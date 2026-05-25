# SaleADS Mi Negocio Full Workflow Test

This folder contains a Playwright E2E test that validates the complete workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google
2. Expand `Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Información General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Términos y Condiciones` (new tab or same tab)
9. Validate `Política de Privacidad` (new tab or same tab)
10. Emit final PASS/FAIL report

## Key Design Rules Implemented

- No hardcoded SaleADS domain.
- Uses visible text-first selectors whenever possible.
- Waits for UI load after each click.
- Handles legal links opening either in current tab or a new tab.
- Captures screenshots at key checkpoints.

## Usage

```bash
cd /workspace/saleads-e2e
npm run install:browsers
SALEADS_START_URL="https://<current-env-login-page>" npm run test:saleads-mi-negocio
```

If your runtime already places the browser on the login page, omit `SALEADS_START_URL`.

## Output Evidence

Playwright outputs are generated under:

- `test-results/` (includes checkpoints and `final-report.json`)
- `playwright-report/` (HTML report)

The final report contains PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
