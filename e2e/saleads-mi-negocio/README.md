# SaleADS Mi Negocio full workflow test

Automated Playwright flow for:

1. Login with Google
2. Open **Negocio > Mi Negocio**
3. Validate **Agregar Negocio** modal
4. Open **Administrar Negocios**
5. Validate account sections and legal links
6. Capture screenshots and final PASS/FAIL report

## Why this is environment-agnostic

- No hardcoded SaleADS domain is used.
- The target environment URL is supplied at runtime through `SALEADS_URL`.
- Selectors prefer visible text and semantic roles.

## Setup

```bash
cd "/workspace/e2e/saleads-mi-negocio"
npm install
npm run install:browsers
```

## Run

```bash
SALEADS_URL="https://<current-env-login-url>" npm run test:mi-negocio
```

Optional:

- `HEADLESS=false` to run headed.

## Output

Artifacts are written to:

`artifacts/saleads-mi-negocio/<timestamp>/`

Including:

- checkpoint screenshots
- `final-report.json` with PASS/FAIL status for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Informacion General
  - Detalles de la Cuenta
  - Tus Negocios
  - Terminos y Condiciones
  - Politica de Privacidad
