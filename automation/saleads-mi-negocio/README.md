# SaleADS Mi Negocio Full Workflow Test

This folder contains the Playwright automation for:

- Login with Google
- Mi Negocio menu validation
- Agregar Negocio modal validation
- Administrar Negocios account sections validation
- Términos y Condiciones and Política de Privacidad validation (same tab or new tab)
- Checkpoint screenshots and final PASS/FAIL report

## Environment-agnostic execution

The test does **not** hardcode any SaleADS domain.  
Provide the login URL of the target environment via `SALEADS_LOGIN_URL`.

```bash
SALEADS_LOGIN_URL="https://<your-saleads-environment>/login" npm run test:saleads-mi-negocio
```

If the test is launched with an already-opened SaleADS login page, `SALEADS_LOGIN_URL` can be omitted.

## Google account selector handling

The flow explicitly looks for and selects:

- `juanlucasbarbiergarzon@gmail.com`

## Artifacts

Playwright output includes:

- Screenshots at key checkpoints
- `saleads_mi_negocio_report.json` with PASS/FAIL per required report field and captured legal URLs
