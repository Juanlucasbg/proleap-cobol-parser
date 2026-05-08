# SaleADS Mi Negocio Full Workflow Test

This repository now includes an end-to-end Playwright test for the workflow named:

- `saleads_mi_negocio_full_test`

It validates the full **Mi Negocio** module flow after Google login and does not hardcode any environment domain.

## File

- `tests/saleads-mi-negocio-full.spec.ts`

## What it validates

The test executes and reports PASS/FAIL for:

1. Login
2. Mi Negocio menu
3. Agregar Negocio modal
4. Administrar Negocios view
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones
9. Política de Privacidad

It also captures screenshots at key checkpoints and writes a final JSON report with evidence paths and legal URLs.

## Environment variables

- `SALEADS_LOGIN_URL` (required when the browser starts on `about:blank`)
  - Example: login URL for dev/staging/prod environment.
- `GOOGLE_ACCOUNT_EMAIL` (optional)
  - Default: `juanlucasbarbiergarzon@gmail.com`
- `HEADLESS` (optional)
  - Set `HEADLESS=false` for headed mode.

## Run

Install dependencies:

```bash
npm install
```

Run only this workflow test:

```bash
npm run test:saleads-mi-negocio
```

Headed mode:

```bash
npm run test:saleads-mi-negocio:headed
```

## Evidence output

Each run writes screenshots and report JSON into:

```text
artifacts/saleads-mi-negocio/<timestamp>/
```

Includes:

- checkpoint screenshots (`.png`)
- `final-report.json` with PASS/FAIL summary by step
