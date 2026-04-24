# SaleADS Playwright E2E Tests

This repository now includes a standalone Playwright suite for SaleADS UI validation.

## Implemented scenario

- `saleads_mi_negocio_full_test`
- File: `tests/saleads.mi-negocio.spec.ts`
- Covers:
  - Google login flow (and continue after login)
  - "Mi Negocio" submenu expansion
  - "Agregar Negocio" modal validation
  - "Administrar Negocios" page sections
  - "Información General", "Detalles de la Cuenta", and "Tus Negocios"
  - Legal links:
    - "Términos y Condiciones"
    - "Política de Privacidad"
  - Screenshots at important checkpoints
  - Final PASS/FAIL report attachment per required fields

## Environment compatibility

- The test does **not** hardcode a domain.
- It assumes the browser is already on the SaleADS login page, per request.
- Optional `SALEADS_BASE_URL` may be set for local runs if desired.

## Run locally

1. Install dependencies:
   - `npm install`
2. Install browsers:
   - `npx playwright install --with-deps chromium`
3. Run the scenario:
   - `npm run test:saleads:mi-negocio`

## Notes

- If Google account chooser appears, the test attempts to select:
  - `juanlucasbarbiergarzon@gmail.com`
- Artifacts (screenshots, trace, report) are saved in Playwright output directories.
