# SaleADS - Mi Negocio Full Workflow E2E

This Playwright test validates the full Mi Negocio workflow requested in the automation brief:

1. Login with Google.
2. Expand `Negocio` -> `Mi Negocio`.
3. Validate the `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate:
   - Terminos y Condiciones
   - Politica de Privacidad
7. Produce a PASS/FAIL final report with legal URLs.

## Run

```bash
SALEADS_URL="https://<your-current-environment-url>" npm run test:saleads-mi-negocio
```

Headed mode (useful for Google auth flows):

```bash
SALEADS_URL="https://<your-current-environment-url>" npm run test:saleads-mi-negocio:headed
```

## Evidence

- Checkpoint screenshots are attached in Playwright results.
- A JSON final report is generated as `saleads-mi-negocio-report.json` in Playwright output.
