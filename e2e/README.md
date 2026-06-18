# SaleADS Mi Negocio full workflow test

This Playwright test automates the full SaleADS.ai Mi Negocio validation flow:

1. Login with Google.
2. Open `Negocio` -> `Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
   - Terminos y Condiciones
   - Politica de Privacidad
6. Generate PASS/FAIL report and screenshot evidence.

## Run

Set a login URL for the current target environment (dev, staging, production, etc.) without hardcoding any domain in the test:

```bash
export SALEADS_START_URL="https://<current-environment>/login"
npm run test:e2e
```

Optional:

- `SALEADS_GOOGLE_ACCOUNT_EMAIL` (defaults to `juanlucasbarbiergarzon@gmail.com`)

## Artifacts

- Screenshots at key checkpoints (attached in Playwright output)
- JSON final report:
  - `test-results/.../saleads-mi-negocio-final-report.json`
  - Includes PASS/FAIL per required field and legal final URLs.
