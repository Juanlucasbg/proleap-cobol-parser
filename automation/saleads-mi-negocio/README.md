# SaleADS Mi Negocio Full Workflow Test

This runner automates the workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google.
2. Open and validate **Mi Negocio** menu.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios** and validate sections.
5. Validate **Información General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Términos y Condiciones** (same tab or new tab).
9. Validate **Política de Privacidad** (same tab or new tab).
10. Produce a PASS/FAIL report.

## Why this works in any environment

- No domain is hardcoded.
- The login page URL is provided at runtime through `SALEADS_LOGIN_URL`.
- UI interactions prefer visible text selectors and include load waits after clicks.

## Setup

```bash
cd /workspace/automation/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run

```bash
SALEADS_LOGIN_URL="https://<your-environment-login-page>" \
SALEADS_GOOGLE_ACCOUNT="juanlucasbarbiergarzon@gmail.com" \
npm run saleads:mi-negocio
```

Optional environment variables:

- `SALEADS_HEADLESS=false` to run with browser UI.
- `SALEADS_WAIT_MS=1500` to increase wait after each click.
- `SALEADS_ARTIFACTS_DIR=./artifacts/saleads-mi-negocio` to customize artifact path.

## Outputs

- Checkpoint screenshots in `artifacts/saleads-mi-negocio/`.
- Final structured JSON report in the same folder.
- Terminal summary listing PASS/FAIL for each required report field.
