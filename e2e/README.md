# SaleADS Mi Negocio E2E

This folder contains an environment-agnostic Playwright script for the workflow:

- Login with Google
- Navigate to **Mi Negocio**
- Validate **Agregar Negocio** modal
- Validate **Administrar Negocios** sections
- Validate **Terminos y Condiciones** and **Politica de Privacidad** pages

## Run

```bash
cd e2e
npm install
SALEADS_LOGIN_URL="https://<current-saleads-environment>/login" npm run saleads:mi-negocio
```

## Environment variables

- `SALEADS_LOGIN_URL` (recommended): login URL for the current SaleADS environment.
- `SALEADS_BASE_URL` (fallback): used when `SALEADS_LOGIN_URL` is not provided.
- `SALEADS_HEADLESS` (optional): defaults to `true`; set to `false` for headed mode.

## Output

The script writes artifacts under:

- `e2e/artifacts/<run-id>/screenshots/*.png`
- `e2e/artifacts/<run-id>/final-report.json`

The final report includes PASS/FAIL status for each requested validation step plus evidence (screenshots and legal URLs).
