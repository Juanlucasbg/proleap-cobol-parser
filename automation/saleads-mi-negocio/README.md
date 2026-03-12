# SaleADS Mi Negocio Full Workflow Automation

This Playwright automation validates the full **Mi Negocio** workflow:

1. Login with Google.
2. Open **Negocio > Mi Negocio**.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios**.
5. Validate:
   - Información General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Términos y Condiciones
   - Política de Privacidad
7. Export evidence and PASS/FAIL report.

## Requirements

- Node.js 18+
- A reachable SaleADS environment URL (dev/staging/prod)
- Valid Google session capability in the runtime browser context

## Setup

```bash
cd /workspace/automation/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run

```bash
SALEADS_LOGIN_URL="https://<your-env-login-url>" npm run test:mi-negocio
```

Optional variables:

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADED=1` to run with visible browser window

## Outputs

Artifacts are written to:

- `automation/saleads-mi-negocio/artifacts/<run-id>/report.json`
- `automation/saleads-mi-negocio/artifacts/<run-id>/summary.txt`
- `automation/saleads-mi-negocio/artifacts/<run-id>/screenshots/*.png`

Also updated on each run:

- `automation/saleads-mi-negocio/artifacts/latest-report.json`
- `automation/saleads-mi-negocio/artifacts/latest-summary.txt`
