# SaleADS Mi Negocio E2E

This folder contains an environment-agnostic Playwright workflow test for:

- Google login
- Mi Negocio menu expansion
- Agregar Negocio modal checks
- Administrar Negocios account page validations
- Términos y Condiciones and Política de Privacidad legal-page checks
- Final PASS/FAIL JSON report by requested section

## Setup

```bash
cd qa
npm install
npx playwright install --with-deps chromium
```

## Run

```bash
cd qa
SALEADS_BASE_URL="https://<your-saleads-environment>" npm run test:saleads-mi-negocio
```

Optional:

- `SALEADS_GOOGLE_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)

## Artifacts

After execution, Playwright artifacts are stored under:

- `qa/artifacts/test-output/**`

Important files include:

- Checkpoint screenshots (`01-*.png` ... `06-*.png`)
- `saleads-mi-negocio-final-report.json` with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
