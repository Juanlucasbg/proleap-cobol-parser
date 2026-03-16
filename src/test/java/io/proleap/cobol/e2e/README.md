## SaleADS Mi Negocio E2E workflow test

Test class: `SaleAdsMiNegocioFullTest`

This test automates the complete workflow requested for:

- Login with Google
- Mi Negocio menu expansion
- Agregar Negocio modal validation
- Administrar Negocios page validation
- Información General validation
- Detalles de la Cuenta validation
- Tus Negocios validation
- Términos y Condiciones validation (including URL capture)
- Política de Privacidad validation (including URL capture)

### Runtime configuration

The test is disabled by default and is enabled with environment variables:

- `SALEADS_E2E_ENABLED=true`
- `SALEADS_START_URL=<current-environment-login-url>`

Optional:

- `SALEADS_HEADLESS=true|false` (default: `true`)
- `SALEADS_TIMEOUT_SECONDS=<seconds>` (default: `30`)

### Evidence output

Screenshots and final report are generated under:

`target/surefire-reports/saleads-mi-negocio/`

Key output file:

- `final-report.md` (PASS/FAIL for each requested validation step plus legal URLs)
