# SaleADS Mi Negocio Full Workflow E2E

This folder contains an environment-agnostic Playwright E2E test for:

- Google login flow (starting from the current SaleADS login page)
- Mi Negocio sidebar navigation
- Agregar Negocio modal validation
- Administrar Negocios sections validation
- Legal links (`Términos y Condiciones`, `Política de Privacidad`) with tab-handling

## Test file

- `tests/saleads_mi_negocio_full_test.spec.ts`

## How to run

```bash
cd automation/saleads-mi-negocio-e2e
npm install
npx playwright install --with-deps
npm test
```

Optional headed run:

```bash
npm run test:headed
```

## Assumptions

- The browser starts on the SaleADS login page for the current environment, or one of these env vars is set:
  - `SALEADS_LOGIN_URL`
  - `SALEADS_URL`
  - `BASE_URL`
- A valid Google session/account is available for:
  - `juanlucasbarbiergarzon@gmail.com`

## Evidence output

The test saves screenshots and a final machine-readable report under:

- `evidence/*.png`
- `evidence/final-report.json`

The final report uses PASS/FAIL values for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
