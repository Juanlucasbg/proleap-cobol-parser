# SaleADS Mi Negocio E2E

This folder contains the test `saleads_mi_negocio_full_test`, implemented with Playwright.

## What it validates

The workflow includes:

1. Login with Google (including account selector when shown).
2. Open **Negocio > Mi Negocio** and validate submenu options.
3. Open and validate **Agregar Negocio** modal.
4. Open **Administrar Negocios** and validate all account sections.
5. Validate:
   - Información General
   - Detalles de la Cuenta
   - Tus Negocios
6. Open and validate:
   - Términos y Condiciones
   - Política de Privacidad
7. Capture screenshots at required checkpoints.
8. Produce a final PASS/FAIL report by validation field.

## Environment support

The test is environment-agnostic: it does not hardcode any SaleADS URL.

Set one of the following environment variables before running:

- `SALEADS_BASE_URL` (recommended)
- `BASE_URL`
- `APP_URL`

Example:

```bash
SALEADS_BASE_URL="https://your-saleads-environment.example.com" npm test
```

## Run

```bash
npm run install:browsers
SALEADS_BASE_URL="https://your-environment" npm test
```

Run headed mode:

```bash
SALEADS_BASE_URL="https://your-environment" npm run test:headed
```

## Artifacts

- Screenshots and traces: `test-results/`
- HTML report: `playwright-report/`
- Step-by-step final report: test attachment `final-report.json`
