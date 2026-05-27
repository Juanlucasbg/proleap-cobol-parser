# SaleADS Mi Negocio Full Workflow Test

This automation validates the complete **Mi Negocio** workflow:

1. Login with Google.
2. Expand **Mi Negocio** menu and validate options.
3. Validate **Agregar Negocio** modal.
4. Open **Administrar Negocios** and validate required sections.
5. Validate **Información General**.
6. Validate **Detalles de la Cuenta**.
7. Validate **Tus Negocios**.
8. Validate **Términos y Condiciones** (including new-tab handling).
9. Validate **Política de Privacidad** (including new-tab handling).
10. Produce a PASS/FAIL report with evidence paths and legal URLs.

## Why this works across environments

- No domain is hardcoded.
- You can provide the target environment at runtime (`SALEADS_BASE_URL`), or connect to an already-open browser page via CDP (`SALEADS_CDP_URL`).
- The script prioritizes selectors by **visible text** and role names.

## Setup

```bash
cd automation/saleads-mi-negocio
npm install
npm run install:browsers
```

## Run

### Option A: URL-based run (dev/staging/prod)

```bash
SALEADS_BASE_URL="https://your-saleads-environment/login" \
SALEADS_GOOGLE_EMAIL="juanlucasbarbiergarzon@gmail.com" \
SALEADS_HEADLESS="false" \
npm run test:mi-negocio
```

### Option B: Use an already-open browser/login page (CDP)

```bash
SALEADS_CDP_URL="http://127.0.0.1:9222" \
SALEADS_GOOGLE_EMAIL="juanlucasbarbiergarzon@gmail.com" \
npm run test:mi-negocio
```

## Environment variables

- `SALEADS_BASE_URL` or `SALEADS_URL` (optional): target login URL.
- `SALEADS_CDP_URL` (optional): connect to an existing Chromium session.
- `SALEADS_GOOGLE_EMAIL` (optional): defaults to `juanlucasbarbiergarzon@gmail.com`.
- `SALEADS_HEADLESS` (optional): `true` or `false`, default `false`.
- `SALEADS_EVIDENCE_DIR` (optional): custom evidence output directory.

## Output

Each execution stores:

- Checkpoint screenshots:
  - `step-1-dashboard-loaded.png`
  - `step-2-mi-negocio-menu-expanded.png`
  - `step-3-agregar-negocio-modal.png`
  - `step-4-administrar-negocios-page.png`
  - `step-8-terminos-y-condiciones.png`
  - `step-9-politica-de-privacidad.png`
- Final JSON report:
  - `final-report.json`

The report includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
