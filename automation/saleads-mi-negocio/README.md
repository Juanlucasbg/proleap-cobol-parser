# SaleADS Mi Negocio Full Workflow Test

This automation validates the full **Mi Negocio** workflow end-to-end:

1. Login with Google
2. Open Mi Negocio menu
3. Validate Agregar Negocio modal
4. Open Administrar Negocios
5. Validate Informacion General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Terminos y Condiciones
9. Validate Politica de Privacidad
10. Print final PASS/FAIL report with evidence

## Why this works across environments

- No hardcoded domain is used in selectors.
- You can pass any environment login URL via `SALEADS_URL`.
- UI interactions are based on visible text and roles.

## Setup

```bash
cd automation/saleads-mi-negocio
npm install
```

## Run

```bash
SALEADS_URL="https://<your-saleads-environment>/login" npm run test:mi-negocio
```

Optional variables:

- `GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS=true|false` (default: `false`)
- `SCREENSHOT_DIR` (default: `artifacts/saleads-mi-negocio/<timestamp>`)

## Output

- Checkpoint screenshots:
  - dashboard loaded
  - Mi Negocio expanded
  - Agregar Negocio modal
  - Administrar Negocios full page
  - Terminos y Condiciones page
  - Politica de Privacidad page
- Final JSON report printed to stdout:
  - PASS/FAIL by requested section
  - legal page final URLs
  - screenshot paths
- JSON report also saved to:
  - `artifacts/saleads-mi-negocio/<timestamp>/final-report.json`
