# SaleADS Mi Negocio E2E

End-to-end Playwright automation for the workflow:

- Login with Google
- Navigate to `Negocio` > `Mi Negocio`
- Validate `Agregar Negocio` modal
- Open `Administrar Negocios`
- Validate:
  - `Información General`
  - `Detalles de la Cuenta`
  - `Tus Negocios`
- Validate legal links:
  - `Términos y Condiciones`
  - `Política de Privacidad`
- Generate final PASS/FAIL report

## Environment agnostic behavior

The test does not hardcode a domain and works in any SaleADS.ai environment.

- By default it assumes the browser is already on the SaleADS login page.
- Optionally set `SALEADS_LOGIN_URL` to force navigation to a login page URL.

## Setup

```bash
cd e2e/saleads-mi-negocio
npm install
npm run install:browsers
```

## Run

```bash
npm run test:mi-negocio
```

Headed mode:

```bash
HEADED=1 npm run test:mi-negocio
```

With explicit login URL:

```bash
SALEADS_LOGIN_URL="https://<your-environment>/login" npm run test:mi-negocio
```

## Artifacts

Generated under `artifacts/`:

- `screenshots/` checkpoint images
- `mi-negocio-final-report.json` with PASS/FAIL per requested section and final legal URLs

Playwright report is generated in `playwright-report/`.
