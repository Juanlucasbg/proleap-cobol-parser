# SaleADS Mi Negocio E2E Test

This Playwright suite validates the complete **Mi Negocio** workflow in any SaleADS environment (dev, staging, production) without hardcoded domains.

## Coverage

The test `saleads-mi-negocio-full-test.spec.js` validates:

1. Login with Google (including account selector for `juanlucasbarbiergarzon@gmail.com` when shown)
2. Sidebar `Mi Negocio` menu expansion
3. `Agregar Negocio` modal fields and controls
4. `Administrar Negocios` page sections
5. `Información General`
6. `Detalles de la Cuenta`
7. `Tus Negocios`
8. `Términos y Condiciones` legal page (tab or same-tab navigation)
9. `Política de Privacidad` legal page (tab or same-tab navigation)
10. Final JSON PASS/FAIL report

## Run

```bash
cd e2e/saleads
npx playwright install chromium
SALEADS_BASE_URL="https://<current-saleads-login-url>" npm run test:mi-negocio
```

Alternative variable names supported: `SALEADS_URL`, `BASE_URL`, `PLAYWRIGHT_TEST_BASE_URL`.

## Artifacts

- Screenshots at important checkpoints
- Playwright HTML report (`playwright-report/`)
- JSON final report attached as `final-report` artifact in `test-results/`
