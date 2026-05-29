# SaleADS Mi Negocio E2E

This folder contains a Playwright end-to-end test for the workflow:

1. Login with Google
2. Navigate to `Negocio` -> `Mi Negocio`
3. Validate `Agregar Negocio` modal
4. Open `Administrar Negocios`
5. Validate account sections
6. Validate legal links (`Términos y Condiciones`, `Política de Privacidad`)
7. Produce a final PASS/FAIL report

## Setup

```bash
cd e2e
npm install
npx playwright install chromium
```

## Run

Set one of these environment variables (no hardcoded domain required):

- `SALEADS_LOGIN_URL` (preferred, direct login page)
- `SALEADS_BASE_URL` (fallback)

You can copy `.env.example` to your own local env file if needed.

```bash
cd e2e
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm run test:saleads-mi-negocio
```

Headed mode:

```bash
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" npm run test:saleads-mi-negocio:headed
```

## Evidence output

- Checkpoint screenshots: `e2e/artifacts/screenshots/`
- Final report: `e2e/artifacts/reports/saleads-mi-negocio-final-report.json`

The report contains PASS/FAIL per required validation group, legal final URLs, and screenshot paths.
