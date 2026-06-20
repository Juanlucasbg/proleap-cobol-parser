# SaleADS Mi Negocio Full Workflow Test

This repository includes a standalone Playwright script that validates the full **Mi Negocio** workflow requested in the automation task:

- Google login and dashboard/sidebar validation
- Mi Negocio submenu expansion
- "Agregar Negocio" modal validation
- "Administrar Negocios" account page sections
- Información General / Detalles de la Cuenta / Tus Negocios validation
- Legal links validation:
  - Términos y Condiciones
  - Política de Privacidad
- Screenshot capture at key checkpoints
- Final PASS/FAIL report per requested field

## Run

```bash
npm install
SALEADS_START_URL="https://<current-saleads-env>/login" npm run saleads:mi-negocio
```

## Environment behavior

- No domain is hardcoded.
- Use `SALEADS_START_URL` for the target environment (`dev`, `staging`, `production`, etc.).
- Script works with `HEADLESS=false` for debugging.

## Artifacts

Output is written to:

```text
artifacts/saleads-mi-negocio/<timestamp>/
```

Including:

- `screenshots/*.png`
- `report.json`
