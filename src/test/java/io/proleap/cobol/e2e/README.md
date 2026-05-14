# SaleADS Mi Negocio E2E test

`SaleAdsMiNegocioFullWorkflowE2ETest` validates the full SaleADS workflow:

1. Login with Google.
2. Open `Negocio -> Mi Negocio`.
3. Validate `Agregar Negocio` modal.
4. Open `Administrar Negocios`.
5. Validate `Información General`, `Detalles de la Cuenta`, and `Tus Negocios`.
6. Validate `Términos y Condiciones` and `Política de Privacidad` (same tab or new tab).
7. Produce a final PASS/FAIL report in the test output.

## Runtime configuration

Required:

- `-Dsaleads.e2e.enabled=true`
- `-Dsaleads.login.url=<environment login URL>`

Optional:

- `-Dsaleads.google.account.email=juanlucasbarbiergarzon@gmail.com`
- `-Dsaleads.e2e.headless=true`
- `-Dsaleads.e2e.timeout.seconds=25`

## Run command

```bash
mvn -Dtest=SaleAdsMiNegocioFullWorkflowE2ETest \
    -Dsaleads.e2e.enabled=true \
    -Dsaleads.login.url=https://<environment-login-url> \
    test
```

Screenshots are written to `target/saleads-e2e-screenshots/`.
