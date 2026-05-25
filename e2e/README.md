# SaleADS Mi Negocio E2E Test

This repository now includes the Playwright test:

- `e2e/saleads-mi-negocio-full.spec.ts`

## Goal

Validate the full SaleADS **Mi Negocio** workflow:

1. Login with Google
2. Open and validate **Mi Negocio** menu
3. Validate **Agregar Negocio** modal
4. Open and validate **Administrar Negocios** view
5. Validate **Información General**
6. Validate **Detalles de la Cuenta**
7. Validate **Tus Negocios**
8. Validate **Términos y Condiciones**
9. Validate **Política de Privacidad**
10. Generate final PASS/FAIL report

## Environment-agnostic behavior

- No domain is hardcoded.
- Provide the login page URL for whichever environment is under test:
  - `SALEADS_LOGIN_URL`
  - or `SALEADS_BASE_URL`
  - or `BASE_URL`
- The same script works for dev/staging/prod by changing only env vars.

## Run

```bash
npx playwright install chromium
SALEADS_LOGIN_URL="https://<current-env-login-page>" npm run test:e2e:headed
```

Optional (headless):

```bash
SALEADS_LOGIN_URL="https://<current-env-login-page>" npm run test:e2e
```

## Notes

- The test tries to select Google account `juanlucasbarbiergarzon@gmail.com` if Google account picker appears.
- Legal-link validations support either same-tab navigation or new-tab behavior.
- Evidence is attached by Playwright:
  - checkpoint screenshots
  - legal page URLs
  - `final-report.json` with step-by-step PASS/FAIL results.
