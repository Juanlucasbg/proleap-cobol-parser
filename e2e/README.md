# SaleADS Mi Negocio E2E

This folder contains the Playwright test:

- `tests/saleads-mi-negocio-full.spec.ts`

## What it validates

The test executes the complete workflow requested for the Mi Negocio module:

1. Login with Google.
2. Open Mi Negocio menu.
3. Validate Agregar Negocio modal.
4. Open Administrar Negocios view.
5. Validate Información General.
6. Validate Detalles de la Cuenta.
7. Validate Tus Negocios.
8. Validate Términos y Condiciones.
9. Validate Política de Privacidad.
10. Generate final PASS/FAIL report by section.

It also captures screenshots at key checkpoints and records final URLs for legal pages.

## Environment setup

The test does not hardcode any SaleADS domain.

Provide one of these environment variables to define the login page when the test starts from `about:blank`:

- `SALEADS_LOGIN_URL`
- `SALEADS_URL`
- `BASE_URL`

Optional:

- `HEADLESS=false` to run headed mode.

## Commands

From this folder:

```bash
npm run install:browsers
npm test
```

Headed mode:

```bash
HEADLESS=false npm run test:headed
```
