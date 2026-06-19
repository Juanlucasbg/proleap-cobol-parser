# SaleADS.ai UI Tests

This folder contains browser automation for the SaleADS "Mi Negocio" flow.

## Implemented scenario

- **Test name:** `saleads_mi_negocio_full_test`
- **File:** `tests/saleads-mi-negocio-full.spec.js`
- **Coverage:** Google login + full Mi Negocio workflow, including:
  - Sidebar navigation checks
  - Mi Negocio expansion and options visibility
  - Agregar Negocio modal validations
  - Administrar Negocios page validations
  - Información General, Detalles de la Cuenta, Tus Negocios validations
  - Términos y Condiciones and Política de Privacidad link validations
  - Screenshot capture at required checkpoints
  - Final PASS/FAIL JSON report with legal-page final URLs

## Prerequisites

1. Node.js 18+
2. Playwright browsers installed:

```bash
npx playwright install
```

## Environment variables

Set these before running:

- `SALEADS_LOGIN_URL` (required): Login page URL for the current SaleADS environment.
  - No domain is hardcoded in test code.
- `SALEADS_GOOGLE_ACCOUNT` (optional): Google account email to choose.
  - Default: `juanlucasbarbiergarzon@gmail.com`
- `SALEADS_EXPECTED_USER_NAME` (optional): Expected display name for strict name validation.

## Run

```bash
npm test
```

or only this scenario:

```bash
npm run test:saleads-mi-negocio
```

## Outputs

Artifacts are generated under:

- `artifacts/run-<timestamp>/`
  - Checkpoint screenshots (`.png`)
  - `final-report.json` with PASS/FAIL per requested section and captured legal URLs
