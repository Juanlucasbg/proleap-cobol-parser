# SaleADS Mi Negocio E2E Test

This folder contains an environment-agnostic Playwright test for the complete **Mi Negocio** workflow.

## What it validates

The test file `saleads-mi-negocio.spec.js` executes and validates:

1. Login with Google (including optional account chooser for `juanlucasbarbiergarzon@gmail.com`)
2. Mi Negocio menu expansion
3. Agregar Negocio modal fields and controls
4. Administrar Negocios page sections
5. Informacion General section
6. Detalles de la Cuenta section
7. Tus Negocios section
8. Terminos y Condiciones page (same tab or new tab)
9. Politica de Privacidad page (same tab or new tab)
10. Final PASS/FAIL report JSON

It also captures screenshots at key checkpoints.

## Run

```bash
cd /workspace/e2e
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<your-environment-login-page>" npm run test:mi-negocio
```

## Output artifacts

- JSON report: `../artifacts/saleads-mi-negocio-report.json`
- Checkpoint screenshots: `../artifacts/saleads-mi-negocio/`
- Playwright HTML report: `../artifacts/playwright-report/`

## Notes

- The test intentionally does not hardcode a SaleADS domain.
- It uses visible-text-first selectors, plus resilient fallbacks.
- If Google login opens a popup or new tab, it handles that flow.
