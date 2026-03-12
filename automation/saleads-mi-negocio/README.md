# SaleADS Mi Negocio full workflow test

This Playwright test automates the complete Mi Negocio flow requested in `saleads_mi_negocio_full_test`:

1. Login with Google.
2. Expand **Mi Negocio** and validate submenu entries.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios** and validate all sections.
5. Validate:
   - Información General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Términos y Condiciones
   - Política de Privacidad
7. Save screenshot evidence and final JSON report.

## Requirements

- Node.js 18+ (validated with Node 22 in this environment)
- Playwright browser binaries installed

## Setup

```bash
cd /workspace/automation/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run

```bash
SALEADS_LOGIN_URL="https://<current-saleads-environment>/login" npm test
```

Notes:

- The test is environment-agnostic and does not hardcode any domain.
- It uses visible text selectors where possible.
- If Google opens account selection, it tries selecting:
  - `juanlucasbarbiergarzon@gmail.com`

## Outputs

- Screenshots and test report are generated in:
  - `automation/saleads-mi-negocio/artifacts/<timestamp>/`
- Final validation summary JSON:
  - `final-report.json`
