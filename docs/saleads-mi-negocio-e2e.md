# SaleADS Mi Negocio E2E Workflow

This repository includes an environment-agnostic Selenium test for the SaleADS "Mi Negocio" module workflow:

- Login with Google
- Open "Mi Negocio" menu
- Validate "Agregar Negocio" modal
- Open "Administrar Negocios"
- Validate sections and legal links
- Generate PASS/FAIL summary and screenshots

## Test class

`io.proleap.cobol.e2e.saleads.SaleadsMiNegocioFullWorkflowTest`

## Required environment variable

- `SALEADS_LOGIN_URL`: login page URL for the current environment (dev/staging/prod)

## Optional environment variables

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_EXPECTED_USER_NAME` (for stricter user-name validation)
- `SALEADS_HEADLESS` (default: `true`)

## Run only this test

```bash
mvn -Dtest=io.proleap.cobol.e2e.saleads.SaleadsMiNegocioFullWorkflowTest test
```

## Evidence output

On execution, screenshots and report are stored under:

`target/saleads-evidence/<utc-timestamp>/`

Files include:

- `01-dashboard-loaded.png`
- `02-mi-negocio-menu-expanded.png`
- `03-agregar-negocio-modal.png`
- `04-administrar-negocios.png`
- `08-terminos-y-condiciones.png`
- `09-politica-de-privacidad.png`
- `summary-report.md`
