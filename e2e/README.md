# SaleADS Mi Negocio workflow E2E

This folder contains the end-to-end test:

- `saleads-mi-negocio-full.spec.ts`

## Purpose

Validate the complete Mi Negocio workflow:

1. Login with Google.
2. Open and validate `Mi Negocio` menu.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Informacion General`.
6. Validate `Detalles de la Cuenta`.
7. Validate `Tus Negocios`.
8. Validate `Terminos y Condiciones` (+ capture URL).
9. Validate `Politica de Privacidad` (+ capture URL).
10. Generate final PASS/FAIL report.

## Environment variables

- `SALEADS_LOGIN_URL` (recommended): login page URL for the current SaleADS environment.
- `SALEADS_BASE_URL` or `BASE_URL` (fallback if `SALEADS_LOGIN_URL` is missing).
- `SALEADS_GOOGLE_ACCOUNT` (optional): account to select in Google chooser.
  - Defaults to `juanlucasbarbiergarzon@gmail.com`.
- `HEADLESS` (optional):
  - `true` by default.
  - Set `HEADLESS=false` for headed execution.

## Run

Install browsers once:

```bash
npx playwright install
```

Run only this workflow test:

```bash
npm run test:saleads-mi-negocio
```

Run headed:

```bash
npm run test:saleads-mi-negocio:headed
```

## Evidence artifacts

- Screenshots at key checkpoints are stored in Playwright test output.
- Final JSON report includes:
  - PASS/FAIL by validation field.
  - Final URLs for legal pages.
