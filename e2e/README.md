# SaleADS Mi Negocio E2E

This directory contains an isolated Playwright test that validates the complete
Mi Negocio workflow requested by automation:

- Login with Google
- Open `Negocio` -> `Mi Negocio`
- Validate `Agregar Negocio` modal
- Open `Administrar Negocios`
- Validate sections and account details
- Validate legal links:
  - `Términos y Condiciones`
  - `Política de Privacidad`
- Capture screenshots at key checkpoints
- Produce a final PASS/FAIL report by required field

## Environment compatibility

The test is URL-agnostic and works in any SaleADS environment (dev/staging/prod):

- If the browser is already on SaleADS login page, no URL is required.
- Or provide `SALEADS_URL` to navigate to the environment login page.

No domain is hardcoded in the test.

## Required/optional environment variables

- `SALEADS_URL` (optional): SaleADS environment URL.
- `SALEADS_GOOGLE_ACCOUNT` (optional): Google account email to select.
  - Default: `juanlucasbarbiergarzon@gmail.com`
- `SALEADS_EXPECTED_USER_NAME` (optional): user name expected in
  `Información General` (if omitted, the test validates that a name-like value
  exists).

## Install

```bash
cd e2e
npm install
npx playwright install --with-deps
```

## Run

Headless:

```bash
cd e2e
SALEADS_URL="https://your-saleads-env.example" npm test
```

Headed:

```bash
cd e2e
SALEADS_URL="https://your-saleads-env.example" npm run test:headed
```

## Outputs and evidence

- HTML report: `e2e/playwright-report/`
- Test artifacts/screenshots: `e2e/test-results/`
- Attached final report in test output:
  `saleads-mi-negocio-final-report`
