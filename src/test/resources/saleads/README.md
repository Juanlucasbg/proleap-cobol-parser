# SaleADS Mi Negocio E2E

This test validates the full Google login + Mi Negocio workflow in any SaleADS.ai environment without hardcoding a domain.

## Test class

- `io.proleap.saleads.SaleadsMiNegocioFullTest`

## Required runtime setup

Use one of these options:

1. **Start URL mode** (recommended for CI):
   - Provide `SALEADS_START_URL` (or `-Dsaleads.start.url=...`) pointing to the current environment login page.
2. **Attach to existing browser mode**:
   - Start Chrome with remote debugging enabled.
   - Provide `SALEADS_DEBUGGER_ADDRESS` (or `-Dsaleads.debugger.address=127.0.0.1:9222`).

Optional variables:

- `SALEADS_GOOGLE_ACCOUNT` (default: `juanlucasbarbiergarzon@gmail.com`)
- `SALEADS_HEADLESS=true` (only used in start URL mode)

## Run command

```bash
mvn -Dtest=io.proleap.saleads.SaleadsMiNegocioFullTest test
```

## Evidence output

Screenshots are saved to:

- `target/saleads-evidence/<timestamp>/`

The test prints a final PASS/FAIL report for:

- Login
- Mi Negocio menu
- Agregar Negocio modal
- Administrar Negocios view
- Información General
- Detalles de la Cuenta
- Tus Negocios
- Términos y Condiciones
- Política de Privacidad
