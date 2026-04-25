# SaleADS E2E - Mi Negocio workflow

This folder contains an environment-agnostic Playwright test for the full
`saleads_mi_negocio_full_test` workflow.

## What it validates

The test covers:

1. Login with Google.
2. Mi Negocio menu expansion.
3. Agregar Negocio modal fields/buttons.
4. Administrar Negocios sections.
5. Información General.
6. Detalles de la Cuenta.
7. Tus Negocios.
8. Términos y Condiciones (same tab or new tab).
9. Política de Privacidad (same tab or new tab).
10. Final PASS/FAIL JSON report.

It also captures screenshots at key checkpoints.

## Prerequisites

- Node.js 18+ recommended.
- Playwright browser binaries installed.

## Run

```bash
cd e2e
npm install
npm run install:browsers
SALEADS_URL="https://<current-environment-login-url>" \
GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com" \
npm run test:saleads:mi-negocio
```

You can also provide `SALEADS_BASE_URL` in place of `SALEADS_URL`.

## Outputs

- Playwright report: `e2e/playwright-report/`
- Raw test artifacts: `e2e/test-results/`
- Final step report JSON:
  `e2e/test-results/saleads_mi_negocio_full_test-*/saleads_mi_negocio_final_report.json`
  and copied flat as:
  `e2e/test-results/saleads_mi_negocio_final_report.json`
