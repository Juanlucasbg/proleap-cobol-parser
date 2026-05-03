# SaleADS Mi Negocio Full Workflow Test

This repository now includes a Selenium/JUnit workflow test named:

- `saleads_mi_negocio_full_test`

Implemented at:

- `src/test/java/io/proleap/ui/SaleAdsMiNegocioFullTest.java`

## What it validates

The test covers:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal
4. Administrar Negocios page sections
5. Información General
6. Detalles de la Cuenta
7. Tus Negocios
8. Términos y Condiciones (including new tab handling)
9. Política de Privacidad (including new tab handling)
10. Final PASS/FAIL report output

The implementation is environment-agnostic:

- It does not hardcode a specific SaleADS domain.
- Base URL is provided at runtime.
- Element lookup prefers visible text and includes fallback selectors.

## Runtime inputs

System properties (all optional except base URL):

- `-Dsaleads.baseUrl=https://<your-saleads-login-url>` (required)
- `-Dsaleads.browser=chrome|firefox|edge` (default: `chrome`)
- `-Dsaleads.headless=true|false` (default: `true`)
- `-Dsaleads.googleEmail=juanlucasbarbiergarzon@gmail.com`
- `-Dsaleads.timeoutSeconds=25`
- `-Dsaleads.screenshotsDir=target/surefire-reports/saleads-mi-negocio`

Environment variable alternative:

- `SALEADS_BASE_URL` can be used instead of `-Dsaleads.baseUrl`.

## Evidence output

The test prints evidence lines to standard output, including:

- Screenshot paths for key checkpoints
- Final URL for:
  - Términos y Condiciones
  - Política de Privacidad

Screenshots are saved under:

- `target/surefire-reports/saleads-mi-negocio/<timestamp>/`

## Final report format

At teardown, the test prints:

- `===== Final Report: saleads_mi_negocio_full_test =====`
- PASS/FAIL for:
  - Login
  - Mi Negocio menu
  - Agregar Negocio modal
  - Administrar Negocios view
  - Información General
  - Detalles de la Cuenta
  - Tus Negocios
  - Términos y Condiciones
  - Política de Privacidad

## Example execution

```bash
mvn -Dtest=io.proleap.ui.SaleAdsMiNegocioFullTest \
    -Dsaleads.baseUrl="https://<environment-login-url>" \
    -Dsaleads.headless=false \
    test
```
