# SaleADS Mi Negocio Full Workflow Test

This folder contains an end-to-end Playwright test for the `saleads_mi_negocio_full_test` automation workflow.

## Goal

Validate the complete Mi Negocio workflow:

1. Login with Google
2. Open Mi Negocio menu
3. Validate Agregar Negocio modal
4. Open Administrar Negocios
5. Validate Informacion General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Terminos y Condiciones
9. Validate Politica de Privacidad
10. Produce a final PASS/FAIL report

## Environment-agnostic behavior

- The test does **not** hardcode a domain.
- It assumes the browser starts on the SaleADS login page.
- Optionally pass `SALEADS_BASE_URL` to navigate directly to a target environment.

## Setup

From this folder:

```bash
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
npm run test:mi-negocio
```

Optional environment variables:

- `SALEADS_BASE_URL`: optional URL to open at test start.
- `SALEADS_GOOGLE_EMAIL`: defaults to `juanlucasbarbiergarzon@gmail.com`.
- `SALEADS_HEADLESS`: defaults to `true`; set to `false` for headed mode.

Example:

```bash
SALEADS_BASE_URL="https://your-env.saleads.ai/login" SALEADS_HEADLESS=false npm run test:mi-negocio
```

## Artifacts

- Screenshots and JSON final report are captured under `artifacts/<run-timestamp>/`.
- Playwright HTML report is in `playwright-report`.

The test also logs a structured final report with PASS/FAIL per requested validation block.
