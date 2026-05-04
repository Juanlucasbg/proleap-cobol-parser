# SaleADS Mi Negocio E2E

This folder contains the automated Playwright test:

- `saleads_mi_negocio_full_test`

It validates the complete Mi Negocio workflow after Google sign-in:

1. Login with Google
2. Expand Mi Negocio menu
3. Validate Agregar Negocio modal
4. Open Administrar Negocios
5. Validate Información General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Términos y Condiciones
9. Validate Política de Privacidad
10. Generate final PASS/FAIL report per step

## Environment requirements

The test is environment-agnostic and does not hardcode any domain.
Provide the entry URL with one of:

- `SALEADS_LOGIN_URL` (preferred)
- `SALEADS_BASE_URL`
- `BASE_URL`
- `PLAYWRIGHT_TEST_BASE_URL`

Example:

```bash
cd /workspace/e2e
cp .env.example .env
# Edit .env to your current SaleADS environment URL
```

## Run

```bash
cd /workspace/e2e
npm install
npx playwright install --with-deps chromium
SALEADS_LOGIN_URL="https://your-env.saleads.ai" npm test
```

For headed mode:

```bash
SALEADS_LOGIN_URL="https://your-env.saleads.ai" npm run test:headed
```

## Evidence and report

The test captures screenshots at key checkpoints and attaches:

- Dashboard loaded
- Mi Negocio menu expanded
- Agregar Negocio modal
- Administrar Negocios full page
- Términos y Condiciones page
- Política de Privacidad page

It also attaches `final-report.json` with:

- PASS/FAIL for each required validation step
- Captured legal page URLs
- Failure details (if any)
