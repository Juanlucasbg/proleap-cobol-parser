## SaleADS Mi Negocio E2E Test

This folder contains `SaleadsMiNegocioFullTest`, an end-to-end workflow test for:

1. Login with Google
2. Mi Negocio sidebar menu validation
3. Agregar Negocio modal validation
4. Administrar Negocios page validation
5. Informacion General validation
6. Detalles de la Cuenta validation
7. Tus Negocios validation
8. Terminos y Condiciones validation (new tab or same tab)
9. Politica de Privacidad validation (new tab or same tab)
10. Final PASS/FAIL report generation

### Execution

The test is opt-in to avoid running during normal CI parser suites.

Required env vars:

- `RUN_SALEADS_E2E=true`
- `SALEADS_LOGIN_URL=<login page URL of current environment>`

Optional env vars:

- `SALEADS_HEADLESS=false` (default is headless true)

Example:

```bash
RUN_SALEADS_E2E=true \
SALEADS_LOGIN_URL="https://<your-saleads-env>/login" \
SALEADS_HEADLESS=false \
mvn -Dtest=io.proleap.e2e.SaleadsMiNegocioFullTest test
```

### Evidence output

Screenshots and final report are written under:

`target/saleads-evidence/<timestamp>/`

Expected files include:

- `01-dashboard-loaded.png`
- `02-mi-negocio-menu-expanded.png`
- `03-crear-negocio-modal.png`
- `04-administrar-negocios-full.png`
- `08-terminos-y-condiciones.png`
- `09-politica-de-privacidad.png`
- `10-final-report.txt`
