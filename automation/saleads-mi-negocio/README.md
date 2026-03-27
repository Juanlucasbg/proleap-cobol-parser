# SaleADS Mi Negocio Full Workflow Test

This module contains an environment-agnostic Playwright automation for:

- Login with Google
- Mi Negocio menu validation
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Informacion General / Detalles de la Cuenta / Tus Negocios validations
- Legal links validation:
  - Terminos y Condiciones
  - Politica de Privacidad

## Why this is environment-agnostic

- It does not hardcode any SaleADS domain.
- It accepts the login URL from environment variables.
- It prefers selectors by visible text.
- It supports legal links that open in same tab or new tab.

## Setup

```bash
cd automation/saleads-mi-negocio
npm install
npm run install:browsers
```

## Run

```bash
SALEADS_LOGIN_URL="<current_env_login_url>" npm test
```

Optional variables:

- `SALEADS_GOOGLE_ACCOUNT_EMAIL` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_TEST_BUSINESS_NAME` (default: `Negocio Prueba Automatizacion`)
- `PW_HEADLESS=false` for headed mode

## Output artifacts

Generated in `automation/saleads-mi-negocio/artifacts/`:

- `report.json` (machine-readable)
- `report.txt` (human-readable final report)
- `screenshots/*.png` (checkpoint screenshots)

The final report returns PASS/FAIL for:

1. Login
2. Mi Negocio menu
3. Agregar Negocio modal
4. Administrar Negocios view
5. Informacion General
6. Detalles de la Cuenta
7. Tus Negocios
8. Terminos y Condiciones
9. Politica de Privacidad
