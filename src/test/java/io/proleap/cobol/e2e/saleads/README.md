# SaleADS Mi Negocio Full Test

This package contains the `SaleadsMiNegocioFullTest` JUnit test, which automates the full workflow requested in `saleads_mi_negocio_full_test`:

1. Login with Google.
2. Open `Negocio` -> `Mi Negocio`.
3. Validate the `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate:
   - Informacion General
   - Detalles de la Cuenta
   - Tus Negocios
6. Validate legal links:
   - Terminos y Condiciones
   - Politica de Privacidad
7. Produce PASS/FAIL results for every required report field.

## Environment requirements

- JDK 17+
- Maven 3+
- Environment variable:
  - `SALEADS_LOGIN_URL`: login URL of the active SaleADS environment (dev/staging/prod)
- Optional:
  - `SALEADS_HEADLESS=true|false` (default `true`)

## Run command

```bash
mvn -Dtest=SaleadsMiNegocioFullTest test
```

## Output artifacts

The test writes evidence under:

`target/saleads-e2e/<timestamp>/`

- Screenshots for dashboard, menu, modal, account page, terms, privacy
- `saleads_mi_negocio_full_test_report.txt` with PASS/FAIL per field:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Informacion General
  - Detalles de la Cuenta
  - Tus Negocios
  - Terminos y Condiciones
  - Politica de Privacidad
