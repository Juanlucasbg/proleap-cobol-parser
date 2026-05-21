# SaleADS Mi Negocio E2E

This repository now includes the Playwright test:

- `tests/saleads.mi-negocio.spec.js`

## Purpose

Covers the complete `saleads_mi_negocio_full_test` workflow:

1. Login with Google
2. Open Mi Negocio menu
3. Validate Agregar Negocio modal
4. Open and validate Administrar Negocios
5. Validate Informacion General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Terminos y Condiciones (same tab or new tab)
9. Validate Politica de Privacidad (same tab or new tab)
10. Emit final PASS/FAIL report by step

The test does not hardcode a SaleADS domain. Set the target environment at runtime.

## Install

```bash
npm install
npx playwright install
```

## Run

```bash
SALEADS_BASE_URL="https://<current-env-login-page>" npm run test:e2e:mi-negocio
```

Headed mode:

```bash
SALEADS_BASE_URL="https://<current-env-login-page>" npm run test:e2e:mi-negocio:headed
```

## Evidence produced

- Checkpoint screenshots are attached to the Playwright test output.
- HTML report: `playwright-report/`
- JSON report: `test-results/playwright-report.json`
- Per-test final report artifact: `saleads-mi-negocio-final-report.json` attached in Playwright output.
