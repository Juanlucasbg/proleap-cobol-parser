# SaleADS Mi Negocio Full Workflow Test

This folder contains an end-to-end Playwright test named:

- `saleads_mi_negocio_full_test`

The test validates the complete Mi Negocio flow (not only login):

1. Login with Google
2. Open and validate the Mi Negocio menu
3. Validate the Agregar Negocio modal
4. Open Administrar Negocios and validate account sections
5. Validate Informacion General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Terminos y Condiciones (including new tab handling)
9. Validate Politica de Privacidad (including new tab handling)
10. Generate a final PASS/FAIL report per validation area

## Environment-agnostic behavior

- The script does not hardcode any SaleADS URL or domain.
- Provide the login URL at runtime so the same test works for dev/staging/prod.

## Prerequisites

- Node.js 18+
- Dependencies installed from this folder:

```bash
npm install
```

- Playwright browser binaries:

```bash
npx playwright install
```

## Runtime variables

- `SALEADS_LOGIN_URL` (recommended): login URL of the current environment.
- `SALEADS_ACCOUNT_EMAIL` (optional): defaults to `juanlucasbarbiergarzon@gmail.com`.
- `SALEADS_ARTIFACTS_DIR` (optional): custom directory for screenshots and final report.
- `HEADLESS=false` (optional): run with visible browser window.

If `SALEADS_LOGIN_URL` is omitted, the test waits for manual navigation away from `about:blank`.

## Run

```bash
npm run test:saleads
```

Headed mode (recommended for Google account selection):

```bash
HEADLESS=false npm run test:saleads
```

## Artifacts

Per run, the test writes:

- Checkpoint screenshots
- `final-report.json` with PASS/FAIL per step and captured legal URLs

Default location:

- `artifacts/saleads-mi-negocio/<timestamp>/`
