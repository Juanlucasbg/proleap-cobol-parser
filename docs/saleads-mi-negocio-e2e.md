# SaleADS Mi Negocio Full Workflow Test

This repository now includes a Playwright E2E test that validates the full Mi Negocio workflow requested in the `saleads_mi_negocio_full_test` automation.

## Test location

- `tests/e2e/saleads-mi-negocio-full.spec.ts`

## What it validates

The test executes and validates all requested checkpoints:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal content
4. Administrar Negocios page sections
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones (including new-tab handling)
9. Politica de Privacidad (including new-tab handling)
10. Final PASS/FAIL report attachment with captured legal URLs

## Environment-agnostic behavior

The test is written to work across SaleADS environments:

- It does **not** hardcode a specific domain.
- It can start from the already-open login page.
- Optionally, you can provide a URL with:
  - `SALEADS_URL`, or
  - `BASE_URL`
- Element selection prefers visible text and semantic roles.

## Required runtime setup

Install browsers once (or when Playwright version changes):

```bash
npm run playwright:install
```

## Run commands

Run only this workflow test:

```bash
npm run test:e2e:mi-negocio
```

Run in headed mode:

```bash
HEADLESS=false npm run test:e2e:mi-negocio
```

If you want to force a starting URL:

```bash
SALEADS_URL="https://<current-saleads-environment>" npm run test:e2e:mi-negocio
```

## Useful environment variables

- `SALEADS_URL` / `BASE_URL`: optional start URL
- `GOOGLE_ACCOUNT_EMAIL`: defaults to `juanlucasbarbiergarzon@gmail.com`
- `HEADLESS`: defaults to headless; set `HEADLESS=false` to view the browser

## Evidence generated

The test captures screenshots at important checkpoints:

- dashboard loaded
- expanded Mi Negocio menu
- Crear Nuevo Negocio modal
- full Administrar Negocios page
- Terminos y Condiciones page
- Politica de Privacidad page

A final JSON report is attached to the Playwright run output as:

- `saleads-mi-negocio-final-report.json`
