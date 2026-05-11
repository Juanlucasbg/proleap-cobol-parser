# SaleADS Mi Negocio Full Workflow Test

This package contains an end-to-end test for the workflow:

- Login with Google
- Open **Mi Negocio** menu
- Validate **Agregar Negocio** modal
- Open **Administrar Negocios**
- Validate:
  - Informacion General
  - Detalles de la Cuenta
  - Tus Negocios
  - Terminos y Condiciones
  - Politica de Privacidad

## Environment-agnostic behavior

The test does **not** hardcode any SaleADS domain.  
Set the login URL at runtime:

```bash
export SALEADS_LOGIN_URL="https://<current-environment>/login"
```

Optional:

```bash
export PLAYWRIGHT_HEADLESS=false
```

## Run

```bash
mvn -Dtest=io.proleap.e2e.saleads.SaleadsMiNegocioFullTest test
```

## Evidence output

Screenshots and report are saved in:

```text
target/evidence/saleads_mi_negocio_full_test/
```

Files include:

- `01-dashboard-loaded.png`
- `02-mi-negocio-menu-expanded.png`
- `03-agregar-negocio-modal.png`
- `04-administrar-negocios-full-page.png`
- `08-terminos-y-condiciones.png`
- `09-politica-de-privacidad.png`
- `final-report.txt`

`final-report.txt` includes PASS/FAIL for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Informacion General
- Detalles de la Cuenta
- Tus Negocios
- Terminos y Condiciones
- Politica de Privacidad
