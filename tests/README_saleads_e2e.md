# SaleADS Mi Negocio E2E

This repository now includes a Playwright test for the workflow:

- Login with Google
- Navigate to **Negocio > Mi Negocio**
- Validate **Agregar Negocio** modal
- Open **Administrar Negocios**
- Validate:
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
- Produce a final PASS/FAIL report by validation section

## Run

```bash
npm run playwright:install
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm run test:e2e:mi-negocio
```

or:

```bash
SALEADS_BASE_URL="https://<your-saleads-env>" npm run test:e2e:mi-negocio
```

## Notes

- No domain is hardcoded; use environment variables for dev/staging/production.
- The Google account selector step attempts to choose:
  - `juanlucasbarbiergarzon@gmail.com`
- Evidence is captured as Playwright screenshots in the test output folder.
- Legal links support same-tab navigation or popup/new-tab behavior.
