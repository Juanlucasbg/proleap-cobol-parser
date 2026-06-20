# SaleADS Mi Negocio full workflow test

This folder contains an end-to-end Playwright runner for the workflow:

1. Login with Google
2. Open Mi Negocio
3. Validate Agregar Negocio modal
4. Open Administrar Negocios
5. Validate Informacion General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Terminos y Condiciones
9. Validate Politica de Privacidad
10. Generate PASS/FAIL report for each validation field

## Requirements

- Node.js 20+ (validated with Node 22)
- A valid SaleADS login URL for the current environment (dev/staging/prod)
- Browser account access for Google login

## Setup

```bash
cd saleads-tests
npm install
npx playwright install chromium
```

## Run

```bash
SALEADS_BASE_URL="https://<current-environment-login-page>" npm run test:mi-negocio
```

Optional:

- `HEADLESS=false` to watch browser execution.

## Output

The script stores artifacts in:

```text
saleads-tests/artifacts/saleads_mi_negocio_full_test/<timestamp>/
```

Generated evidence:

- Screenshots for major checkpoints
- `report.json` with PASS/FAIL per required report field
- Final legal URLs for:
  - Terminos y Condiciones
  - Politica de Privacidad
