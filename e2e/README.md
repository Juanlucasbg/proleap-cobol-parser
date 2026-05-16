# SaleADS Mi Negocio E2E

This folder contains the test `saleads_mi_negocio_full_test` that validates:

1. Google login
2. Mi Negocio menu
3. Agregar Negocio modal
4. Administrar Negocios view
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones
9. Política de Privacidad

## Why it is environment-agnostic

The test does not hardcode any SaleADS domain.  
Use `SALEADS_LOGIN_URL` to point to the current environment login page (dev/staging/prod).

## Setup

```bash
cd /workspace/e2e
npm install
npm run install:browsers
```

## Run

```bash
cd /workspace/e2e
SALEADS_LOGIN_URL="https://<current-environment-login-url>" \
SALEADS_GOOGLE_ACCOUNT="juanlucasbarbiergarzon@gmail.com" \
npm run test:saleads-mi-negocio
```

## Artifacts

The test writes evidence under:

```text
e2e/artifacts/saleads_mi_negocio_full_test/<timestamp>/
```

Generated evidence includes:

- Dashboard screenshot after login
- Expanded Mi Negocio menu screenshot
- Agregar Negocio modal screenshot
- Full screenshot of Administrar Negocios page
- Términos y Condiciones screenshot
- Política de Privacidad screenshot
- `final-report.json` with PASS/FAIL per requested section and final legal URLs
