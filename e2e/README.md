# SaleADS Mi Negocio E2E

This folder contains an environment-agnostic Playwright test for the complete
`saleads_mi_negocio_full_test` workflow:

- Login with Google
- Open `Negocio` -> `Mi Negocio`
- Validate `Agregar Negocio` modal
- Open `Administrar Negocios`
- Validate account sections
- Validate legal links (`Términos y Condiciones`, `Política de Privacidad`)
- Capture screenshots at key checkpoints
- Emit a final PASS/FAIL report per step

## Requirements

- Node.js 18+
- Playwright Chromium browser installed

## Install

```bash
cd e2e
npm install
npx playwright install chromium
```

## Run

Set one of the following environment variables:

- `SALEADS_LOGIN_URL` (preferred): exact login page URL of current environment
- `SALEADS_BASE_URL`: fallback if login URL equals base URL

Examples:

```bash
cd e2e
SALEADS_LOGIN_URL="https://your-saleads-env/login" npm test
```

or

```bash
cd e2e
SALEADS_BASE_URL="https://your-saleads-env" npm test
```

## Notes

- No domain is hardcoded.
- Locators prefer visible text and role-based selectors.
- The test supports legal links opening in either the same tab or a popup.
- Screenshots and report artifacts are stored under Playwright output folders.
