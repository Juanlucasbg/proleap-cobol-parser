# SaleADS Mi Negocio Full Workflow Test

This Playwright suite automates the workflow named `saleads_mi_negocio_full_test`:

1. Login with Google
2. Open `Negocio` -> `Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate `Informacion General`
6. Validate `Detalles de la Cuenta`
7. Validate `Tus Negocios`
8. Validate `Terminos y Condiciones` (same tab or new tab)
9. Validate `Politica de Privacidad` (same tab or new tab)
10. Emit final PASS/FAIL report with captured legal URLs

## Environment-agnostic behavior

- No domain is hardcoded.
- The login page URL is injected at runtime.
- Selectors prioritize visible text and roles.
- The script waits for UI loading after each click.

## Prerequisites

- Node.js 20+
- Browser dependencies installed by Playwright

## Install

```bash
cd qa/saleads-mi-negocio
npm install
npx playwright install --with-deps
```

## Run

```bash
cd qa/saleads-mi-negocio
SALEADS_LOGIN_URL="https://<current-saleads-environment>/login" npm test
```

Optional:

- `npm run test:headed` to run with browser UI
- `npm run test:ui` to use Playwright UI mode

## Evidence and report artifacts

- Screenshots are stored in `qa/saleads-mi-negocio/artifacts/run-<timestamp>/`
- Structured report is attached as `final-report.json` in Playwright results
- Console includes a `FINAL_REPORT` JSON summary with:
  - PASS/FAIL per required step
  - Final Terms URL
  - Final Privacy URL
