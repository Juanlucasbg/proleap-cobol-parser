# SaleADS Mi Negocio Full Workflow Test

This Playwright suite validates the full **Mi Negocio** flow using visible-text selectors and environment-driven URLs so it can run in dev/staging/production without hardcoding domains.

## What this test covers

`tests/saleads-mi-negocio-full.spec.ts` executes and validates:

1. Login with Google (including account selection when shown).
2. Sidebar > Negocio > Mi Negocio expansion.
3. "Agregar Negocio" modal content and controls.
4. "Administrar Negocios" account page sections.
5. "Información General" key data.
6. "Detalles de la Cuenta" key data.
7. "Tus Negocios" key data.
8. "Términos y Condiciones" link navigation/new-tab handling.
9. "Política de Privacidad" link navigation/new-tab handling.
10. Final JSON report with PASS/FAIL by requested report fields.

The suite captures screenshots at important checkpoints and attaches a `final-report.json` artifact.

## Setup

```bash
cd qa/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Configuration

Set the login URL for the current environment:

```bash
export SALEADS_LOGIN_URL="https://<current-environment>/login"
```

`SALEADS_URL` is accepted as fallback.

## Run

```bash
npm run test:saleads-mi-negocio
```

For headed execution:

```bash
npm run test:saleads-mi-negocio:headed
```

## Notes

- No fixed domain is used by the test.
- The account used for Google selector validation is:
  - `juanlucasbarbiergarzon@gmail.com`
- If Google account selector is not shown, the test continues with the authenticated state.
