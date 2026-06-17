# SaleADS Mi Negocio Full Workflow Test

This Playwright test automates the complete workflow requested for SaleADS.ai:

1. Login with Google.
2. Navigate to **Negocio > Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Terminos y Condiciones
   - Politica de Privacidad
7. Generate final PASS/FAIL report for every required section.

## Environment-agnostic setup

No SaleADS domain is hardcoded. Provide the active environment login page at runtime:

```bash
export SALEADS_LOGIN_URL="https://<current-environment>/login"
```

Alternative variable names supported:

- `SALEADS_BASE_URL`
- `BASE_URL`

## Install and run

```bash
cd /workspace/qa/saleads-mi-negocio
npm install
npx playwright install --with-deps
npm test
```

Run in headed mode:

```bash
npm run test:headed
```

## Notes

- The test tries to pick the Google account `juanlucasbarbiergarzon@gmail.com` if the account selector appears.
- It always waits for UI load after each click.
- It uses visible text selectors whenever possible.
- It handles legal links opening either in the same tab or a new tab.
- Screenshots are captured and attached at key checkpoints.
