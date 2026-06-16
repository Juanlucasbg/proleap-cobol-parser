# SaleADS Mi Negocio Full Workflow Test

This suite automates the complete workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google.
2. Open `Negocio` -> `Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios` and validate sections.
5. Validate:
   - `Información General`
   - `Detalles de la Cuenta`
   - `Tus Negocios`
6. Validate legal links:
   - `Términos y Condiciones`
   - `Política de Privacidad`
7. Produce a final PASS/FAIL report.

## Why this is environment-agnostic

- No hardcoded domain is used.
- The login page URL is supplied by environment variable for the current environment.
- Selectors prioritize visible text in Spanish UI labels.

## Setup

```bash
cd /workspace/automation/saleads-mi-negocio
npm install
npx playwright install chromium
```

## Run

```bash
SALEADS_LOGIN_URL="https://<your-env>/login" npm run test:mi-negocio
```

Optional:

- Run headed mode:

```bash
SALEADS_LOGIN_URL="https://<your-env>/login" npm run test:mi-negocio:headed
```

- Custom artifacts directory:

```bash
SALEADS_LOGIN_URL="https://<your-env>/login" SALEADS_ARTIFACTS_DIR="/tmp/saleads-artifacts" npm run test:mi-negocio
```

## Outputs

The test stores evidence in `artifacts/<timestamp>/` (or `SALEADS_ARTIFACTS_DIR`), including:

- Step screenshots at required checkpoints.
- `final-report.json` with PASS/FAIL per required field.
- `final-report.md` summary including captured legal URLs.
