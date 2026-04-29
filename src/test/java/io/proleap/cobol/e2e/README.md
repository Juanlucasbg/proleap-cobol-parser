# SaleADS Mi Negocio E2E workflow

This package contains an environment-agnostic Playwright + JUnit workflow test:

- `SaleadsMiNegocioWorkflowTest`

The test validates:

1. Login with Google (including account picker when visible)
2. Sidebar > Mi Negocio menu expansion
3. "Agregar Negocio" modal fields and actions
4. "Administrar Negocios" sections
5. "Informacion General"
6. "Detalles de la Cuenta"
7. "Tus Negocios"
8. "Terminos y Condiciones" legal page + final URL
9. "Politica de Privacidad" legal page + final URL
10. Final PASS/FAIL report by step

## Required environment variables

- `SALEADS_LOGIN_URL` (required): login page URL for the current environment.
  - Example: `https://dev.example/login`, `https://staging.example/login`, `https://app.example/login`
  - The test does not hardcode any domain.

## Optional environment variables

- `SALEADS_STORAGE_STATE_PATH`: Playwright storage state JSON path if a pre-authenticated state is used.
- `SALEADS_HEADLESS`: set to `false` to run headed.
- `SALEADS_BROWSER_CHANNEL`: browser channel for Playwright Chromium launch.

## Run

```bash
export SALEADS_LOGIN_URL="https://your-current-saleads-environment/login"
mvn -Dtest=SaleadsMiNegocioWorkflowTest test
```

If you run with shell environment variables exported, the test reads them with `System.getenv`.

## Evidence output

Artifacts are generated under:

`target/e2e-artifacts/saleads-mi-negocio/<timestamp>/`

Expected files:

- `01-dashboard-loaded.png`
- `02-mi-negocio-menu-expanded.png`
- `03-agregar-negocio-modal.png`
- `04-administrar-negocios-page.png`
- `08-terminos-y-condiciones.png`
- `09-politica-de-privacidad.png`
- `10-final-report.txt`
