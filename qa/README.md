# SaleADS Mi Negocio Full Workflow Test

This script validates the complete **Mi Negocio** flow:

1. Login with Google
2. Open `Mi Negocio` menu
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate account sections
6. Validate `Terminos y Condiciones`
7. Validate `Politica de Privacidad`
8. Produce PASS/FAIL final report

## Why this is environment-agnostic

- No domain is hardcoded.
- The login URL is provided at runtime through environment variables.
- Selectors prioritize visible text over brittle CSS.

## Requirements

- Node.js 20+
- Run once in `qa/`:

```bash
npm install
npx playwright install chromium
```

## Run

From repository root:

```bash
cd qa
SALEADS_LOGIN_URL="https://<current-environment-login-page>" npm run saleads:mi-negocio
```

Optional environment variables:

- `SALEADS_GOOGLE_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_HEADLESS` (`true` or `false`, default `false`)
- `SALEADS_TIMEOUT_MS` (default `30000`)
- `SALEADS_ARTIFACT_DIR` (default `qa/artifacts/<timestamp>`)

## Evidence and report

- Checkpoint screenshots are stored in the artifact directory.
- `final-report.json` includes PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
