# SaleADS Mi Negocio E2E

This folder contains a Playwright E2E test for:

- Login with Google
- Full Mi Negocio module workflow validation
- Legal links validation (including new tab handling)
- Checkpoint screenshots and final PASS/FAIL report

## Test file

- `tests/saleads-mi-negocio.spec.js`

## Run locally

```bash
cd /workspace/ui-e2e
npm install
npx playwright install --with-deps chromium
npm run test:saleads
```

## Environment-agnostic behavior

- The test **does not hardcode any SaleADS URL/domain**.
- It assumes the browser is already on the SaleADS login page, matching the task requirement.
- Optional fallback: set `SALEADS_START_URL` if your runner starts from `about:blank`.

Example:

```bash
SALEADS_START_URL="https://your-current-saleads-environment/login" npm run test:saleads
```

## Evidence generated

Playwright test output includes:

- Dashboard screenshot after login
- Expanded Mi Negocio menu screenshot
- Crear Nuevo Negocio modal screenshot
- Administrar Negocios full page screenshot
- Términos y Condiciones screenshot + final URL
- Política de Privacidad screenshot + final URL
- Final step report JSON with PASS/FAIL for each required block
