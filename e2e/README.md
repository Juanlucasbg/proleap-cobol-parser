# SaleADS E2E Tests

This folder contains Playwright end-to-end tests for SaleADS workflows.

## Mi Negocio full workflow test

Test file:

- `tests/saleads-mi-negocio-full.spec.js`

This test validates:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal content
4. Administrar Negocios page sections
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones (new tab or same tab)
9. Política de Privacidad (new tab or same tab)
10. Final PASS/FAIL report

## Configuration

Required environment variable:

- `SALEADS_LOGIN_URL`: login page URL for the current target environment (dev/staging/prod).

Optional environment variables:

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `HEADLESS` (`false` to run headed, default runs headless)

## Run

```bash
cd e2e
npm install
npx playwright install chromium
SALEADS_LOGIN_URL="https://your-environment-login-url" npm run test:saleads-mi-negocio
```

Artifacts:

- Screenshots and JSON report are generated under `e2e/test-results/saleads-mi-negocio/<timestamp>/`.
