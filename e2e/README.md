# SaleADS E2E Automation

This folder contains standalone Playwright automation for the SaleADS workflow:

- `saleads_mi_negocio_full_test`

## Purpose

Validate the full Mi Negocio flow after Google login, including:

1. Login and dashboard/sidebar validation
2. Mi Negocio menu expansion
3. Agregar Negocio modal validation
4. Administrar Negocios page validation
5. Informacion General section checks
6. Detalles de la Cuenta section checks
7. Tus Negocios section checks
8. Terminos y Condiciones validation (+ URL capture)
9. Politica de Privacidad validation (+ URL capture)
10. Final PASS/FAIL JSON report

## Environment-Agnostic Execution

The test is designed to run in any SaleADS environment without hardcoding a domain.

- If your runner already starts on the SaleADS login page, no base URL is needed.
- Optionally set one of:
  - `SALEADS_URL`
  - `BASE_URL`

## Install

```bash
cd e2e
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
cd e2e
npm run test:saleads-mi-negocio
```

## Artifacts and Evidence

- Checkpoint screenshots: `e2e/artifacts/screenshots/`
- Playwright output and attachments: `e2e/test-results/`
- HTML report: `e2e/playwright-report/`
- Final structured report attachment: `saleads-mi-negocio-report.json`

## Notes

- Selectors prioritize visible text and accessibility roles.
- The test includes waits after clicks and handles both same-tab and new-tab legal page navigation.
