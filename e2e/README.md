# SaleADS Mi Negocio E2E

This folder contains the Playwright workflow test:

- `saleads-mi-negocio.spec.ts`

## Run

1. Install browsers:

```bash
npm run e2e:install-browsers
```

2. Run the workflow test against any SaleADS environment:

```bash
SALEADS_LOGIN_URL="https://<your-env-login-url>" npm run e2e:saleads-mi-negocio
```

Optional account selector override:

```bash
SALEADS_GOOGLE_ACCOUNT_EMAIL="juanlucasbarbiergarzon@gmail.com"
```

## Evidence

The test captures screenshots at required checkpoints and writes a final JSON report with PASS/FAIL status for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
