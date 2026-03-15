# SaleADS Mi Negocio Full Workflow E2E

This repository now includes an opt-in Selenium test that automates the flow:

- Login with Google
- Navigate to **Mi Negocio**
- Validate **Agregar Negocio** modal
- Validate **Administrar Negocios** sections
- Validate legal links (**Términos y Condiciones**, **Política de Privacidad**)
- Capture screenshots and a final PASS/FAIL report

## Test class

`src/test/java/io/proleap/cobol/e2e/saleads/SaleadsMiNegocioFullTest.java`

## Configuration

The test is domain-agnostic and uses runtime settings:

- `saleads.e2e.enabled` (or `SALEADS_E2E_ENABLED`) -> must be `true` to run
- `saleads.login.url` (or `SALEADS_LOGIN_URL`) -> login page URL for the current environment
- `saleads.headless` (or `SALEADS_HEADLESS`) -> `true` or `false` (default `false`)

## Example run

```bash
mvn -Dtest=SaleadsMiNegocioFullTest \
    -Dsaleads.e2e.enabled=true \
    -Dsaleads.login.url="https://<current-environment>/login" \
    test
```

## Evidence artifacts

Artifacts are written under:

`target/saleads-artifacts/saleads_mi_negocio_full_test_<timestamp>/`

Including:

- checkpoint screenshots
- error screenshots (if any)
- `final_report.txt` with PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad
