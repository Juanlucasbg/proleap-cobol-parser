# SaleADS Mi Negocio E2E

This folder contains a Playwright end-to-end test for the `saleads_mi_negocio_full_test` workflow:

- Login with Google
- Open **Negocio > Mi Negocio**
- Validate **Agregar Negocio** modal
- Validate **Administrar Negocios** sections
- Validate legal links (**Terminos y Condiciones** and **Politica de Privacidad**)
- Generate a final PASS/FAIL JSON report
- Capture screenshots at key checkpoints

## Requirements

- Node.js 18+
- A reachable SaleADS login URL for the target environment

## Install

```bash
cd saleads-e2e
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
cd saleads-e2e
SALEADS_LOGIN_URL="https://<your-environment-login-page>" npm test
```

No domain is hardcoded. The test runs against whichever environment URL you provide at runtime.

## Artifacts

After execution:

- `test-results/saleads_mi_negocio_full_test_report.json` (final PASS/FAIL report)
- `test-results/checkpoints/*.png` (checkpoint screenshots)
- `test-results/results.json` (Playwright JSON result)
