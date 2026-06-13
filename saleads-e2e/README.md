# SaleADS Mi Negocio E2E

This Playwright suite automates the `saleads_mi_negocio_full_test` workflow:

1. Login with Google
2. Open **Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate **Información General**
6. Validate **Detalles de la Cuenta**
7. Validate **Tus Negocios**
8. Validate **Términos y Condiciones**
9. Validate **Política de Privacidad**
10. Emit a final PASS/FAIL report for each required validation field

## Why it is environment-agnostic

- No hardcoded SaleADS domain is used.
- The test accepts a runtime URL via `SALEADS_LOGIN_URL`.
- If the browser is already on the login page, the test can continue from there.
- Selectors prioritize visible text in Spanish (as requested).

## Run

```bash
cd saleads-e2e
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://<your-current-saleads-env-login>" npm test
```

Optional:

- Headed mode: `npm run test:headed`
- Debug mode: `npm run test:debug`

## Evidence artifacts

Artifacts are saved under Playwright output folders:

- Important checkpoint screenshots
- Legal-document screenshots
- `final_report.json` with PASS/FAIL per requested report field and legal-page final URLs
