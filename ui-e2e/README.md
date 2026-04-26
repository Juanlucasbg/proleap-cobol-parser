# SaleADS Mi Negocio E2E

This folder contains a Playwright test for the full **Mi Negocio** workflow:

- Login with Google
- Open and validate Mi Negocio submenu
- Validate Agregar Negocio modal
- Open and validate Administrar Negocios sections
- Validate legal links (including new-tab behavior)
- Generate PASS/FAIL final report

## Environment variables

- `SALEADS_APP_URL` (optional): environment URL to open before starting. If omitted, test assumes the browser is already on the login page.
- `SALEADS_USER_EMAIL` (optional): Google account/email to select and validate. Default: `juanlucasbarbiergarzon@gmail.com`
- `SALEADS_USER_NAME` (optional): expected visible user name in Información General.
- `HEADLESS` (optional): `false` to run headed.

## Install

```bash
npm install
npx playwright install --with-deps
```

## Run

```bash
npm run test:mi-negocio
```

or headed:

```bash
npm run test:headed -- tests/saleads_mi_negocio_full_test.spec.ts
```

## Evidence output

Artifacts are written to:

- `artifacts/saleads_mi_negocio_full_test/*.png`
- `artifacts/saleads_mi_negocio_full_test/legal-urls.json`
- `artifacts/saleads_mi_negocio_full_test/final-report.json`
