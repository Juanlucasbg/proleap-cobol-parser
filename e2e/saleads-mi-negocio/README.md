# SaleADS - Mi Negocio Full Workflow Test

This folder contains the automation script `saleads_mi_negocio_full_test.js` for validating the complete "Mi Negocio" workflow in any SaleADS.ai environment (dev/staging/prod), without hardcoding a domain in the test logic.

## What the test validates

The script executes the full flow:

1. Login with Google.
2. Expand "Mi Negocio" menu.
3. Validate "Agregar Negocio" modal.
4. Open "Administrar Negocios".
5. Validate "Información General".
6. Validate "Detalles de la Cuenta".
7. Validate "Tus Negocios".
8. Validate "Términos y Condiciones" (same tab or new tab).
9. Validate "Política de Privacidad" (same tab or new tab).
10. Generate final PASS/FAIL report per required field.

The test captures screenshots at key checkpoints and saves final URLs for legal pages.

## Prerequisites

- Node.js 20+.
- Playwright dependency installed in this repository (`npm install` at repo root).

## Environment variables

- `SALEADS_LOGIN_URL` (required): login URL for the current environment.
- `SALEADS_GOOGLE_ACCOUNT` (optional): defaults to `juanlucasbarbiergarzon@gmail.com`.
- `SALEADS_HEADLESS` (optional): `true` (default) or `false`.
- `SALEADS_SLOWMO_MS` (optional): default `200`.
- `SALEADS_EVIDENCE_DIR` (optional): default `evidence/saleads-mi-negocio`.

## Run

From this folder:

```bash
npm run saleads:mi-negocio
```

Example:

```bash
SALEADS_LOGIN_URL="https://your-env.saleads.ai/login" SALEADS_HEADLESS="false" npm run saleads:mi-negocio
```

Install Chromium once (required before first run):

```bash
npx playwright install chromium
```

## Artifacts

For each run, artifacts are generated in:

`evidence/saleads-mi-negocio/<timestamp>/`

Including:

- `screenshots/*.png`
- `final-report.json`

