# SaleADS Mi Negocio workflow automation

This folder contains an environment-agnostic Playwright workflow script for validating the full **SaleADS "Mi Negocio"** flow:

1. Login with Google
2. Open sidebar > Negocio > Mi Negocio
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate:
   - Información General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Términos y Condiciones
   - Política de Privacidad
7. Produce a final PASS/FAIL report

The script uses visible-text selectors whenever possible, waits for UI state after each click, handles legal links opening in a new tab, and captures screenshots at required checkpoints.

## Run

```bash
npm install
npm run saleads:install-browsers
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm run saleads:mi-negocio
```

Optional variables:

- `HEADLESS=false` to run with a visible browser.
- `SALEADS_GOOGLE_ACCOUNT` to override account selector validation (`juanlucasbarbiergarzon@gmail.com` by default).

Artifacts:

- Screenshots and evidence are written to `artifacts/saleads-mi-negocio/`.
