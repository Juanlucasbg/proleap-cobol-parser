# SaleADS E2E: Mi Negocio full workflow

This folder contains the automated browser test:

- `saleads_mi_negocio_full_test`

It validates the full requested workflow:

1. Login with Google
2. Open/validate Mi Negocio menu
3. Validate Agregar Negocio modal
4. Open Administrar Negocios
5. Validate Informacion General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Terminos y Condiciones
9. Validate Politica de Privacidad
10. Produce a final PASS/FAIL report by section

## Environment-agnostic usage

This test does **not** hardcode a SaleADS domain. Provide the login URL for the environment under test:

```bash
SALEADS_START_URL="https://<current-environment>/login" npm run test:saleads-mi-negocio:headed
```

If your execution environment already opens the login page before the test starts, you can omit `SALEADS_START_URL`.

## Install and run

From this directory:

```bash
npm install
npm run install:browsers
npm run test:saleads-mi-negocio:headed
```

## Evidence output

Artifacts are generated in:

- `artifacts/saleads_mi_negocio_full_test/`

Including:

- checkpoint screenshots from key steps
- `final-report.json` with PASS/FAIL per requested report field
- final URLs captured for legal pages
