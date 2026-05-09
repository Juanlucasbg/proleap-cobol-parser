# SaleADS Mi Negocio Full Workflow Test

This Playwright suite automates the `saleads_mi_negocio_full_test` workflow:

- Login with Google.
- Navigate to `Mi Negocio`.
- Validate `Agregar Negocio` modal.
- Validate `Administrar Negocios` sections.
- Validate legal links (`Términos y Condiciones`, `Política de Privacidad`) including new-tab handling.
- Capture screenshots at important checkpoints.
- Generate a final PASS/FAIL report for each requested validation area.

## Environment-agnostic execution

No domain is hardcoded. Set the login URL for the environment you want to test:

```bash
export SALEADS_LOGIN_URL="https://<current-environment>/login"
```

Optional:

```bash
export SALEADS_GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com"
export SALEADS_ARTIFACTS_DIR="/absolute/path/for/artifacts"
export SALEADS_TIMEOUT_MS="30000"
```

## Install and run

```bash
cd /workspace/ui-tests/saleads-mi-negocio
npm install
npx playwright install --with-deps chromium
npm run test:saleads-mi-negocio
```

## Output evidence

Artifacts are written to `ui-tests/saleads-mi-negocio/artifacts` by default:

- Checkpoint screenshots:
  - `01-dashboard-loaded.png`
  - `02-mi-negocio-menu-expanded.png`
  - `03-agregar-negocio-modal.png`
  - `04-administrar-negocios-page.png`
  - `05-terminos-y-condiciones.png`
  - `06-politica-de-privacidad.png`
- Final report:
  - `saleads-mi-negocio-report.json`

The JSON report includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
