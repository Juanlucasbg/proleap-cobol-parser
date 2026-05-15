# SaleADS Mi Negocio end-to-end workflow test

This folder contains the Playwright test `saleads-mi-negocio-full.spec.js`, which validates the complete Mi Negocio workflow:

1. Login with Google
2. Open Mi Negocio menu
3. Validate Agregar Negocio modal
4. Open Administrar Negocios view
5. Validate Información General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Términos y Condiciones (including new-tab handling)
9. Validate Política de Privacidad (including new-tab handling)
10. Emit PASS/FAIL final report for all required report fields

## Usage

Set the login URL for the current environment (dev/staging/production) and run:

```bash
SALEADS_LOGIN_URL="https://<your-environment-login-url>" npm run test:saleads-mi-negocio
```

The test is environment-agnostic and does not hardcode a SaleADS domain.

## Evidence artifacts

Artifacts are written under:

```text
test-results/saleads-mi-negocio/
```

Including checkpoint screenshots and:

```text
final-report.json
```
