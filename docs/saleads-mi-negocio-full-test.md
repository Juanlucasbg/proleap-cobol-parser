# SaleADS Mi Negocio Full Test

This repository now includes an end-to-end Selenium test:

- `src/test/java/io/proleap/cobol/e2e/SaleadsMiNegocioFullTest.java`

## Goal

Validate the full "Mi Negocio" workflow after Google login, including:

1. Login and dashboard/sidebar validation
2. Mi Negocio menu expansion
3. Agregar Negocio modal checks
4. Administrar Negocios view checks
5. Informacion General checks
6. Detalles de la Cuenta checks
7. Tus Negocios checks
8. Terminos y Condiciones checks (new tab/same tab aware)
9. Politica de Privacidad checks (new tab/same tab aware)
10. Final PASS/FAIL report generation

## Environment-Agnostic Inputs

The test does not hardcode a specific domain.

Optional:

- `SALEADS_LOGIN_URL`: login URL for the target SaleADS environment (dev/staging/prod). If not set, the test continues from the browser's current page.
- `SALEADS_SELENIUM_REMOTE_URL`: Selenium Grid endpoint (if remote browser is used)
- `SALEADS_HEADLESS`: `true` (default) or `false`

## Run

```bash
mvn -Dtest=SaleadsMiNegocioFullTest test
```

## Evidence

The test stores screenshots and final report JSON in:

- `target/saleads-evidence/<timestamp>/`

Artifacts include:

- checkpoint screenshots for dashboard/menu/modal/account/legal pages
- `final-report.json` with PASS/FAIL per required field and captured legal URLs
