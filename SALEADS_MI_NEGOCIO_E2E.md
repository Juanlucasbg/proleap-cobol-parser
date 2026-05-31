# SaleADS Mi Negocio - Manual E2E Workflow

This repository now includes a manual Playwright-based test that validates:

1. Login with Google.
2. Sidebar navigation and "Mi Negocio" menu expansion.
3. "Agregar Negocio" modal validations.
4. "Administrar Negocios" view sections.
5. "Informacion General" validations.
6. "Detalles de la Cuenta" validations.
7. "Tus Negocios" validations.
8. "Terminos y Condiciones" legal navigation + URL capture.
9. "Politica de Privacidad" legal navigation + URL capture.
10. Final PASS/FAIL report generation by section.

## Location

`src/test/java/io/proleap/cobol/e2e/saleads/SaleadsMiNegocioWorkflowTestManual.java`

## Runtime requirements

- `SALEADS_LOGIN_URL`: Login URL for the current SaleADS environment (dev/staging/prod).
  - The test never hardcodes a specific domain.
- Optional: `SALEADS_HEADLESS` (`true` or `false`, default `true`).

## Run

```bash
SALEADS_LOGIN_URL="https://<current-environment>/login" \
SALEADS_HEADLESS="true" \
mvn -Dtest=SaleadsMiNegocioWorkflowTestManual test
```

## Evidence output

When the test runs, screenshots and final report are written to:

`target/saleads-e2e-artifacts/<timestamp>/`

Files include:

- `01-dashboard-loaded.png`
- `02-mi-negocio-menu-expanded.png`
- `03-agregar-negocio-modal.png`
- `04-administrar-negocios-full.png`
- `08-terminos-y-condiciones.png`
- `09-politica-de-privacidad.png`
- `final-report.txt`
