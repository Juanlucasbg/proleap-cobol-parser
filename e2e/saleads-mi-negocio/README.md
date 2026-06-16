# SaleADS Mi Negocio Full Workflow Test

This folder contains a Playwright test that validates the complete SaleADS.ai **Mi Negocio** flow:

1. Login with Google.
2. Open `Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate:
   - `Informacion General`
   - `Detalles de la Cuenta`
   - `Tus Negocios`
6. Open and validate legal pages:
   - `Terminos y Condiciones`
   - `Politica de Privacidad`
7. Generate a final PASS/FAIL report.

## Environment-agnostic behavior

- No domain is hardcoded.
- The login page URL is provided at runtime with an environment variable.
- The same test can run in dev, staging, or production.

## Prerequisites

- Node.js 20+
- Chromium browser installed for Playwright:

```bash
npx playwright install chromium
```

## Required environment variables

- `SALEADS_LOGIN_URL`: Login page URL for the current environment.
- `SALEADS_GOOGLE_ACCOUNT_EMAIL` (optional): Google account to select in account chooser.
  - Default: `juanlucasbarbiergarzon@gmail.com`

Example:

```bash
export SALEADS_LOGIN_URL="https://your-environment.example.com/login"
export SALEADS_GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com"
```

## Run

From this folder:

```bash
npm test
```

For headed execution:

```bash
npm run test:headed
```

## Artifacts

After execution, artifacts are written to:

- `test-results/screenshots/` (checkpoint screenshots)
- `test-results/saleads-mi-negocio-report.json` (machine-readable report)
- `test-results/saleads-mi-negocio-report.md` (human-readable report)
- `playwright-report/` (HTML report)
