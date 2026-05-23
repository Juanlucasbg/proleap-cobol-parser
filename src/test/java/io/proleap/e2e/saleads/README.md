# SaleADS Mi Negocio E2E test

This folder contains `SaleadsMiNegocioFullTest`, a live Selenium workflow test
that validates:

1. Google login flow
2. Mi Negocio sidebar expansion
3. Agregar Negocio modal
4. Administrar Negocios page sections
5. Informacion General validations
6. Detalles de la Cuenta validations
7. Tus Negocios validations
8. Terminos y Condiciones legal link (tab or same-page flow)
9. Politica de Privacidad legal link (tab or same-page flow)

## Why this test is opt-in

The test depends on a real SaleADS environment and live credentials. To avoid
breaking default CI runs, it executes only when explicitly enabled.

## Required environment variables

- `SALEADS_RUN_E2E=true`
- `SALEADS_LOGIN_URL=<current environment login URL>`

## Optional environment variables

- `SALEADS_HEADLESS=true|false` (default: `true`)

## Run command

```bash
mvn -Dtest=SaleadsMiNegocioFullTest test
```

## Evidence output

Screenshots and captured legal URLs are saved under:

`target/saleads-mi-negocio-evidence/<timestamp>/`
