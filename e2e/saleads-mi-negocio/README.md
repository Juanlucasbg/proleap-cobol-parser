# SaleADS Mi Negocio Full Workflow Test

This folder contains a standalone Playwright end-to-end test that validates the full
SaleADS "Mi Negocio" module workflow after Google login.

## Scope

The test implements:

1. Login with Google (starting from the login page already opened).
2. Open sidebar "Negocio" > "Mi Negocio".
3. Validate "Agregar Negocio" modal.
4. Open "Administrar Negocios".
5. Validate "Informacion General" section.
6. Validate "Detalles de la Cuenta" section.
7. Validate "Tus Negocios" section.
8. Validate "Terminos y Condiciones" legal link (same tab or new tab).
9. Validate "Politica de Privacidad" legal link (same tab or new tab).
10. Emit explicit PASS/FAIL report fields for all required validations.

The test does not hardcode environment URL/domain and relies on the current page.

## Prerequisites

- Node.js 18+ (recommended 20+)
- Browser opened in the SaleADS login page before execution
- Optional env var for starting URL:
  - `SALEADS_URL` (if omitted, test assumes browser session starts on login page)

## Install

```bash
cd e2e/saleads-mi-negocio
npm install
npx playwright install --with-deps
```

## Run

Headless:

```bash
npm test
```

Headed:

```bash
npm run test:headed
```

## Artifacts

- Screenshots are saved under `test-results/` and attached to the HTML report.
- Full report is generated in `playwright-report/`.
