# SaleADS Mi Negocio workflow test

This standalone Playwright project validates the full `saleads_mi_negocio_full_test` workflow:

1. Login with Google
2. Expand `Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Información General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Términos y Condiciones` (+ URL capture)
9. Validate `Política de Privacidad` (+ URL capture)
10. Emit final PASS/FAIL report per section

## Design constraints covered

- No hardcoded SaleADS environment URL.
- Selectors prioritize visible text (`getByRole` / `getByText`).
- Waits after interactions to let UI settle.
- Handles legal links opening in same tab or new tab.
- Captures screenshots at key checkpoints.

## Prerequisites

- Node.js 20+
- Playwright browsers installed

## Install

```bash
cd qa/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
```

## Run

If your runtime does not preload the login page, pass a URL dynamically:

```bash
SALEADS_LOGIN_URL="https://<current-saleads-environment>/login" npm test
```

You can also use:

```bash
SALEADS_URL="https://<current-saleads-environment>/login" npm test
```

If neither variable is set, the test uses the currently opened page URL.

## Outputs

Playwright output folder contains:

- Step screenshots (`01-dashboard-loaded.png`, etc.)
- `final-report.json` with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
